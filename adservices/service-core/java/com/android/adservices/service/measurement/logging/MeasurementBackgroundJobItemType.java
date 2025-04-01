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

/**
 * Enum class to store measurement background jobs metadata. Used in MeasurementBackgroundItemsInfo
 * in stats/atoms/adservices/adservices_extension_atoms.proto
 */
public enum MeasurementBackgroundJobItemType {
    UNKNOWN_TYPE("UNKNOWN_TYPE", 0),
    SOURCE("SOURCE", 1),
    TRIGGER("TRIGGER", 2),
    EVENT_REPORT("EVENT_REPORT", 3),
    AGGREGATE_REPORT("AGGREGATE_REPORT", 4);

    private final String mItemType;
    private final int mTypeId;

    MeasurementBackgroundJobItemType(String itemType, int typeId) {
        mItemType = itemType;
        mTypeId = typeId;
    }

    /**
     * Gets the type of a background job item.
     *
     * @return the item type string
     */
    public String getItemType() {
        return mItemType;
    }

    /**
     * Gets the id of the item type.
     *
     * @return the item type id
     */
    public int getTypeId() {
        return mTypeId;
    }
}
