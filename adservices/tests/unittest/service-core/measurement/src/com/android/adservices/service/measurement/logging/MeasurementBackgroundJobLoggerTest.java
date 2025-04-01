/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.measurement.logging;

import static com.android.adservices.service.stats.AdServicesStatsLog.ADSERVICES_MEASUREMENT_BACKGROUND_JOB_INFO;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import android.util.proto.ProtoOutputStream;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;

@SpyStatic(AdServicesStatsLog.class)
public class MeasurementBackgroundJobLoggerTest extends AdServicesExtendedMockitoTestCase {
    private static final long PROTO_START_TOKEN = 2246267895809L;
    private static final long ITEM_TYPE_FIELD_ID = 1120986464257L;
    private static final long NUMBER_OF_ITEMS_FIELD_ID = 1120986464258L;
    private static final long OLDEST_TIMESTAMP_FIELD_ID = 1120986464259L;

    @Test
    public void testLogMeasurementBackgroundJobStats() {
        int jobId = 1;
        int executionDurationMs = 2;
        int executionResultCode = 4;
        int stopReason = 5;
        int itemType = MeasurementBackgroundJobItemType.SOURCE.getTypeId();
        int numberOfItems = 10;
        long oldestTimestamp = 100L;
        MeasurementBackgroundItemsInfo itemStats =
                MeasurementBackgroundItemsInfo.builder()
                        .setItemType(itemType)
                        .setNumberOfItems(numberOfItems)
                        .setOldestItemTimestamp(oldestTimestamp)
                        .build();

        // Mock to let AdServicesStatsLog do NOT actually upload logs.
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), any(), any(), anyInt(), anyInt(),
                                        anyInt()));
        MeasurementBackgroundJobInfo stats =
                MeasurementBackgroundJobInfo.builder()
                        .setJobId(jobId)
                        .setJobDurationMs(executionDurationMs)
                        .setDatabaseItemsBeforeProcessing(ImmutableList.of(itemStats))
                        .setItemsProcessed(
                                ImmutableList.of(itemStats, itemStats)) // The item is retried.
                        .setExecutionResultCode(executionResultCode)
                        .setStopReason(stopReason)
                        .build();

        MeasurementBackgroundJobLogger.logMeasurementBackgroundJobStats(stats);

        verify(
                () ->
                        AdServicesStatsLog.write(
                                ADSERVICES_MEASUREMENT_BACKGROUND_JOB_INFO,
                                jobId,
                                protoStreamHelper(List.of(itemStats)),
                                protoStreamHelper(List.of(itemStats, itemStats)),
                                executionDurationMs,
                                executionResultCode,
                                stopReason));
    }

    private byte[] protoStreamHelper(List<MeasurementBackgroundItemsInfo> jobItemStats) {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();
        for (MeasurementBackgroundItemsInfo jobItem : jobItemStats) {
            long token = protoOutputStream.start(PROTO_START_TOKEN);
            protoOutputStream.write(ITEM_TYPE_FIELD_ID, jobItem.getItemType());
            protoOutputStream.write(NUMBER_OF_ITEMS_FIELD_ID, jobItem.getNumberOfItems());
            protoOutputStream.write(OLDEST_TIMESTAMP_FIELD_ID, jobItem.getOldestItemTimestamp());
            protoOutputStream.end(token);
        }
        return protoOutputStream.getBytes();
    }
}
