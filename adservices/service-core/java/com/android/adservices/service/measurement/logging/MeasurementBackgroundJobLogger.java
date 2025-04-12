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

import android.annotation.NonNull;
import android.util.proto.ProtoOutputStream;

import com.android.adservices.service.stats.AdServicesStatsLog;

import java.util.List;

/** Logger for measurement background job metrics. */
public final class MeasurementBackgroundJobLogger {
    // From RepeatedMeasurementBackgroundItemsInfo in
    // stats/atoms/adservices/adservices_extension_atoms.proto
    private static final int ITEM_INFO_FIELD_ID = 1;

    // From MeasurementBackgroundItemsInfo in
    // stats/atoms/adservices/adservices_extension_atoms.proto
    private static final int ITEM_TYPE_FIELD_ID = 1;
    private static final int NUMBER_OF_ITEMS_FIELD_ID = 2;
    private static final int OLDEST_ITEM_TIMESTAMP_FIELD_ID = 3;

    /** Logs measurement background job stats. */
    public static void logMeasurementBackgroundJobStats(MeasurementBackgroundJobInfo stats) {
        AdServicesStatsLog.write(
                ADSERVICES_MEASUREMENT_BACKGROUND_JOB_INFO,
                stats.getJobId(),
                encodeItemInfoToBytes(stats.getDatabaseItemsBeforeProcessing()),
                encodeItemInfoToBytes(stats.getItemsProcessed()),
                stats.getJobDurationMs(),
                stats.getExecutionResultCode(),
                stats.getStopReason());
    }

    /**
     * Encode a list of MeasurementBackgroundItemsInfo Class into a bytes array of atom
     * RepeatedMeasurementBackgroundItemsInfo based on <a
     * href="https://developers.google.com/protocol-buffers/docs/encoding">Protobuf Encoding</a>
     */
    @NonNull
    private static byte[] encodeItemInfoToBytes(
            @NonNull List<MeasurementBackgroundItemsInfo> jobItemInfo) {
        // Creating proto to log nested field RepeatedMeasurementBackgroundItemsInfo.
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();

        for (MeasurementBackgroundItemsInfo jobItem : jobItemInfo) {
            long startFieldId =
                    // MeasurementBackgroundItemsInfo field is repeated.
                    ProtoOutputStream.FIELD_COUNT_REPEATED
                            // items_info is represented by message type.
                            | ProtoOutputStream.FIELD_TYPE_MESSAGE
                            // Field ID of items_info field in
                            // RepeatedMeasurementBackgroundItemsInfo proto.
                            | ITEM_INFO_FIELD_ID;
            long itemTypeFieldId =
                    ProtoOutputStream.FIELD_COUNT_SINGLE
                            // item_type is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of item_type field in MeasurementBackgroundItemsInfo proto.
                            | ITEM_TYPE_FIELD_ID;
            long numberOfItemsFieldId =
                    ProtoOutputStream.FIELD_COUNT_SINGLE
                            // number_of_items is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of number_of_items field in MeasurementBackgroundItemsInfo
                            // proto.
                            | NUMBER_OF_ITEMS_FIELD_ID;
            long oldestItemTimestampFieldId =
                    ProtoOutputStream.FIELD_COUNT_SINGLE
                            // oldest_item_timestamp is represented by int64 type.
                            | ProtoOutputStream.FIELD_TYPE_INT64
                            // Field ID of oldest_item_timestamp field in
                            // MeasurementBackgroundItemsInfo proto.
                            | OLDEST_ITEM_TIMESTAMP_FIELD_ID;
            long token = protoOutputStream.start(startFieldId);
            protoOutputStream.write(itemTypeFieldId, jobItem.getItemType());
            protoOutputStream.write(numberOfItemsFieldId, jobItem.getNumberOfItems());
            protoOutputStream.write(oldestItemTimestampFieldId, jobItem.getOldestItemTimestamp());
            protoOutputStream.end(token);
        }
        return protoOutputStream.getBytes();
    }
}
