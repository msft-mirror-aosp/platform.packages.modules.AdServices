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

import static com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import static com.android.adservices.service.signals.evict.EvictionPriority.EVICT_LATER;
import static com.android.adservices.service.signals.evict.EvictionPriority.EVICT_SOONER;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_FIFO;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_PRIORITIZED_FIFO;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedStats;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.util.Set;

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
    private static final int TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE = 600;
    private static final int TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE = SIZE_LARGE;
    private static final Set<Integer> EVICTOR_TYPES =
            Set.of(SIGNAL_EVICTOR_FIFO, SIGNAL_EVICTOR_PRIORITIZED_FIFO);
    private static final Set<EvictionPriority> UPDATE_SIGNAL_EVICTION_PRIORITIES =
            Set.of(EVICT_LATER);
    private static final Set<EvictionPriority> EVICTED_SIGNAL_EVICTION_PRIORITIES =
            Set.of(EVICT_SOONER);
    private static final byte[] BYTES_1 = new byte[] {0x01, 0x02};
    private static final byte[] BYTES_2 = new byte[] {0x03, 0x04};
    private static final ByteBuffer BYTE_BUFFER_1 = ByteBuffer.wrap(BYTES_1);
    private static final ByteBuffer BYTE_BUFFER_2 = ByteBuffer.wrap(BYTES_2);
    private static final Set<ByteBuffer> UPDATED_SIGNALS_WITH_EVICTION_PRIORITY =
            Set.of(BYTE_BUFFER_1, BYTE_BUFFER_2);

    @Mock private Clock mClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerImpl;
    private ArgumentCaptor<UpdateSignalsProcessReportedStats> mArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mUpdateSignalsProcessReportedLoggerImpl =
                new UpdateSignalsProcessReportedLoggerImpl(mAdServicesLoggerMock, mClockMock);
        mArgumentCaptor = ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);
        when(mClockMock.elapsedRealtime()).thenReturn(TEST_UPDATE_SIGNALS_END_TIME);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_successLogging() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V1);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V1);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_addToSets() {
        mUpdateSignalsProcessReportedLoggerImpl.addSignalEvictorUsed(SIGNAL_EVICTOR_FIFO);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalEvictionPriority(EVICT_LATER);
        mUpdateSignalsProcessReportedLoggerImpl.addEvictedSignalEvictionPriority(EVICT_SOONER);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalWithEvictionPriorityForCount(
                BYTE_BUFFER_1);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(Set.of(SIGNAL_EVICTOR_FIFO));
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount()).isEqualTo(1);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_addToSetsWillDedup() {
        mUpdateSignalsProcessReportedLoggerImpl.addSignalEvictorUsed(
                SIGNAL_EVICTOR_PRIORITIZED_FIFO);
        mUpdateSignalsProcessReportedLoggerImpl.addSignalEvictorUsed(
                SIGNAL_EVICTOR_PRIORITIZED_FIFO);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalEvictionPriority(EVICT_LATER);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalEvictionPriority(EVICT_LATER);
        mUpdateSignalsProcessReportedLoggerImpl.addEvictedSignalEvictionPriority(EVICT_SOONER);
        mUpdateSignalsProcessReportedLoggerImpl.addEvictedSignalEvictionPriority(EVICT_SOONER);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalWithEvictionPriorityForCount(
                BYTE_BUFFER_1);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalWithEvictionPriorityForCount(
                BYTE_BUFFER_1);
        mUpdateSignalsProcessReportedLoggerImpl.addUpdatedSignalWithEvictionPriorityForCount(
                BYTE_BUFFER_1);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getSignalEvictorsUsed())
                .isEqualTo(Set.of(SIGNAL_EVICTOR_PRIORITIZED_FIFO));
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount()).isEqualTo(1);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingUpdateSignalsStartTime() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingAdservicesApiStatusCode() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingSignalsWrittenAndValuesCount() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(0);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(0);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingKeysStoredCount() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(0);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingEvictionRulesCount() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(0);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingPerBuyerSignalSize() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingMaxRawProtectedSignalsSizeBytes() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes()).isEqualTo(0F);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingMinRawProtectedSignalsSizeBytes() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes()).isEqualTo(0F);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingSignalEvictorsUsed() {
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
        // Skip setSignalEvictorsUsed() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEmpty();
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingUpdatedSignalEvictionPriorities() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        // Skip setUpdatedSignalEvictionPriorities() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities()).isEmpty();
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingEvictedSignalEvictionPriorities() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        // Skip setEvictedSignalEvictionPriorities() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities()).isEmpty();
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingPerBuyerEvictedSignalSize() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        // Skip setPerBuyerEvictedSignalSize() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void
            testUpdateSignalsProcessReportedStatsLogger_missingUpdatedSignalsWithEvictionPriorityCount() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        // Skip setUpdatedSignalsWithEvictionPriorityForCount() on purpose
        mUpdateSignalsProcessReportedLoggerImpl.setSignalUpdateSchemaVersion(
                UpdateSchemaVersion.V0);

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount()).isEqualTo(SIZE_UNSET);
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(UpdateSchemaVersion.V0);
    }

    @Test
    public void testUpdateSignalsProcessReportedStatsLogger_missingSignalUpdateSchemaVersion() {
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
        mUpdateSignalsProcessReportedLoggerImpl.setSignalEvictorsUsed(EVICTOR_TYPES);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalEvictionPriorities(
                UPDATE_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setEvictedSignalEvictionPriorities(
                EVICTED_SIGNAL_EVICTION_PRIORITIES);
        mUpdateSignalsProcessReportedLoggerImpl.setPerBuyerEvictedSignalSize(
                TEST_EXACT_PER_BUYER_EVICTED_SIGNAL_SIZE);
        mUpdateSignalsProcessReportedLoggerImpl.setUpdatedSignalsWithEvictionPriorityForCount(
                UPDATED_SIGNALS_WITH_EVICTION_PRIORITY);
        // Skip setSignalUpdateSchemaVersion() on purpose

        mUpdateSignalsProcessReportedLoggerImpl.logUpdateSignalsProcessReportedStats();

        // Verify the logging of UpdateSignalsProcessReportedStats
        verify(mAdServicesLoggerMock)
                .logUpdateSignalsProcessReportedStats(mArgumentCaptor.capture());

        UpdateSignalsProcessReportedStats stats = mArgumentCaptor.getValue();
        expect.that(stats.getUpdateSignalsProcessLatencyMillis())
                .isEqualTo(TEST_UPDATE_SIGNALS_PROCESS_LATENCY_MILLIS);
        expect.that(stats.getAdservicesApiStatusCode()).isEqualTo(TEST_ADSERVICES_API_STATUS_CODE);
        expect.that(stats.getSignalsWrittenCount()).isEqualTo(TEST_SIGNALS_WRITTEN_COUNT);
        expect.that(stats.getKeysStoredCount()).isEqualTo(TEST_KEYS_STORED_COUNT);
        expect.that(stats.getValuesStoredCount()).isEqualTo(TEST_VALUES_STORED_COUNT);
        expect.that(stats.getEvictionRulesCount()).isEqualTo(TEST_EVICTION_RULES_COUNT);
        expect.that(stats.getPerBuyerSignalSize()).isEqualTo(TEST_BUCKETED_PER_BUYER_SIGNAL_SIZE);
        expect.that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MEAN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MAX_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(TEST_MIN_RAW_PROTECTED_SIGNALS_SIZE_BYTES);
        expect.that(stats.getSignalEvictorsUsed()).isEqualTo(EVICTOR_TYPES);
        expect.that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(UPDATE_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(EVICTED_SIGNAL_EVICTION_PRIORITIES);
        expect.that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(TEST_BUCKETED_PER_BUYER_EVICTED_SIGNAL_SIZE);
        expect.that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(UPDATED_SIGNALS_WITH_EVICTION_PRIORITY.size());
        expect.that(stats.getSignalUpdateSchemaVersion()).isEqualTo(FIELD_UNSET);
    }
}
