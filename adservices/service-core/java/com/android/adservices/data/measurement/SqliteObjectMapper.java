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

package com.android.adservices.data.measurement;

import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class for SQLite operations.
 */
class SqliteObjectMapper {

    /**
     * Create {@link EventReport} object from SQLite datastore.
     */
    static EventReport constructEventReportFromCursor(Cursor cursor) {
        EventReport.Builder builder = new EventReport.Builder();
        setTextColumn(cursor, MeasurementTables.EventReportContract.ID,
                builder::setId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.SOURCE_ID,
                builder::setSourceId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                builder::setTriggerPriority);
        setIntColumn(cursor, MeasurementTables.EventReportContract.STATUS,
                builder::setStatus);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DATA,
                builder::setTriggerData);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                builder::setTriggerDedupKey);
        setUriColumn(cursor, MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setUriColumn(cursor, MeasurementTables.EventReportContract.REPORT_TO,
                builder::setReportTo);
        setLongColumn(cursor, MeasurementTables.EventReportContract.REPORT_TIME,
                builder::setReportTime);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_TIME,
                builder::setTriggerTime);
        return builder.build();
    }

    /**
     * Create {@link Source} object from SQLite datastore.
     */
    static Source constructSourceFromCursor(Cursor cursor) {
        Source.Builder builder = new Source.Builder();
        setTextColumn(cursor, MeasurementTables.SourceContract.ID,
                builder::setId);
        setLongColumn(cursor, MeasurementTables.SourceContract.EVENT_ID,
                builder::setEventId);
        setLongColumn(cursor, MeasurementTables.SourceContract.PRIORITY,
                builder::setPriority);
        setUriColumn(cursor, MeasurementTables.SourceContract.REPORT_TO,
                builder::setReportTo);
        setUriColumn(cursor, MeasurementTables.SourceContract.ATTRIBUTION_SOURCE,
                builder::setAttributionSource);
        setUriColumn(cursor, MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setTextColumn(cursor, MeasurementTables.SourceContract.SOURCE_TYPE,
                (enumValue) -> builder.setSourceType(Source.SourceType.valueOf(enumValue)));
        setLongColumn(cursor, MeasurementTables.SourceContract.EXPIRY_TIME,
                builder::setExpiryTime);
        setLongColumn(cursor, MeasurementTables.SourceContract.EVENT_TIME,
                builder::setEventTime);
        setTextColumn(cursor, MeasurementTables.SourceContract.DEDUP_KEYS,
                (concatArray) ->  builder.setDedupKeys(Arrays.stream(concatArray.split(","))
                        .map(String::trim)
                        .filter(String::isEmpty)
                        .map(Long::parseLong)
                        .collect(Collectors.toList())));
        setIntColumn(cursor, MeasurementTables.SourceContract.STATUS,
                builder::setStatus);
        setUriColumn(cursor, MeasurementTables.SourceContract.REGISTERER,
                builder::setRegisterer);
        setIntColumn(cursor, MeasurementTables.SourceContract.ATTRIBUTION_MODE,
                builder::setAttributionMode);
        return builder.build();
    }

    /**
     * Create {@link Trigger} object from SQLite datastore.
     */
    static Trigger constructTriggerFromCursor(Cursor cursor) {
        Trigger.Builder builder = new Trigger.Builder();
        setTextColumn(cursor, MeasurementTables.TriggerContract.ID,
                builder::setId);
        setLongColumn(cursor, MeasurementTables.TriggerContract.PRIORITY,
                builder::setPriority);
        setUriColumn(cursor, MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setUriColumn(cursor, MeasurementTables.TriggerContract.REPORT_TO,
                builder::setReportTo);
        setIntColumn(cursor, MeasurementTables.TriggerContract.STATUS,
                builder::setStatus);
        setLongColumn(cursor, MeasurementTables.TriggerContract.TRIGGER_DATA,
                builder::setTriggerData);
        setLongColumn(cursor, MeasurementTables.TriggerContract.DEDUP_KEY,
                builder::setDedupKey);
        setLongColumn(cursor, MeasurementTables.TriggerContract.TRIGGER_TIME,
                builder::setTriggerTime);
        setUriColumn(cursor, MeasurementTables.TriggerContract.REGISTERER,
                builder::setRegisterer);
        return builder.build();
    }

    /**
     * Create {@link AdtechUrl} object from SQLite datastore.
     */
    static AdtechUrl constructAdtechUrlFromCursor(Cursor cursor) {
        AdtechUrl.Builder builder = new AdtechUrl.Builder();
        setTextColumn(cursor, MeasurementTables.AdTechUrlsContract.POSTBACK_URL,
                builder::setPostbackUrl);
        setTextColumn(cursor, MeasurementTables.AdTechUrlsContract.AD_TECH_ID,
                builder::setAdtechId);
        return builder.build();
    }

    private static <BuilderType> void setUriColumn(Cursor cursor, String column, Function<Uri,
            BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, (x) -> setter.apply(Uri.parse(x)));
    }

    private static <BuilderType> void setIntColumn(Cursor cursor, String column,
                                                   Function<Integer, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getInt, setter);
    }

    private static <BuilderType> void setLongColumn(Cursor cursor, String column,
                                                    Function<Long, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, setter);
    }

    private static <BuilderType> void setTextColumn(Cursor cursor, String column,
                                                    Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor, String column, Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }
}
