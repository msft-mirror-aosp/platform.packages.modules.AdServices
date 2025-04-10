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

import com.google.auto.value.AutoValue;

/**
 * Class for MeasurementBackgroundItemsInfo atom in
 * stats/atoms/adservices/adservices_extension_atoms.proto.
 */
@AutoValue
public abstract class MeasurementBackgroundItemsInfo {
    /**
     * @return the item type of the background job. Enum managed by {@link
     *     MeasurementBackgroundJobItemType}
     */
    public abstract int getItemType();

    /**
     * @return the number of items for this item type.
     */
    public abstract int getNumberOfItems();

    /**
     * @return the timestamp (in milliseconds) of the oldest item for this item type.
     */
    public abstract long getOldestItemTimestamp();

    /** Creates an instance for {@link MeasurementBackgroundItemsInfo.Builder}. */
    public static MeasurementBackgroundItemsInfo.Builder builder() {
        return new AutoValue_MeasurementBackgroundItemsInfo.Builder();
    }

    /** Builder class for {@link MeasurementBackgroundItemsInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Sets the item type of the background job. Enum managed by {@link
         * MeasurementBackgroundJobItemType}
         */
        public abstract Builder setItemType(int value);

        /** Sets the number of items for this item type. */
        public abstract Builder setNumberOfItems(int numberOfItems);

        /** Sets the timestamp (in milliseconds) of the oldest item for this item type. */
        public abstract Builder setOldestItemTimestamp(long oldestItemTimestamp);

        /** Creates an instance for {@link MeasurementBackgroundItemsInfo}. */
        public abstract MeasurementBackgroundItemsInfo build();
    }
}
