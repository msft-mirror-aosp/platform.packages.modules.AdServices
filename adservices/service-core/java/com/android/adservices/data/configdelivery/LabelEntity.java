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

package com.android.adservices.data.configdelivery;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import com.google.auto.value.AutoValue;

/** Table representing Configuration Labels. */
@Entity(
        tableName = LabelEntity.TABLE_NAME,
        primaryKeys = {"config_row_id", "label"},
        foreignKeys = {
            @ForeignKey(
                    onDelete = ForeignKey.CASCADE,
                    entity = ConfigurationEntity.class,
                    parentColumns = {"config_row_id"},
                    childColumns = {"config_row_id"}),
        })
@AutoValue
public abstract class LabelEntity {
    public static final String TABLE_NAME = "labels";

    /** Row ID of the configuration record. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "config_row_id")
    public abstract long getConfigRowId();

    /**
     * Label to filter configuration entries.
     *
     * <p>Labels can be used to filter configuration entries in the client.
     */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "label")
    public abstract String getLabel();

    /** Returns a {@link Builder} for {@link LabelEntity} */
    public static Builder builder() {
        return new AutoValue_LabelEntity.Builder();
    }

    /**
     * Room requires a 'create' method for AutoValue generated objects.
     *
     * <p>Creates and returns a {@link LabelEntity} object.
     */
    public static LabelEntity create(long configRowId, String label) {

        return builder().setConfigRowId(configRowId).setLabel(label).build();
    }

    /** Provides a builder to create an instance of {@link LabelEntity} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getConfigRowId()} */
        public abstract Builder setConfigRowId(long configRowId);

        /** For more details see {@link #getLabel()} */
        public abstract Builder setLabel(String label);

        /**
         * @return an instance of {@link LabelEntity}
         */
        public abstract LabelEntity build();
    }
}
