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
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import com.google.auto.value.AutoValue;

/** Table representing Configurations. */
@Entity(
        tableName = ConfigurationEntity.TABLE_NAME,
        indices = {@Index(value = {"type", "version", "id"}, unique = true)},
        primaryKeys = {"config_row_id"})
@AutoValue
public abstract class ConfigurationEntity {
    public static final String TABLE_NAME = "configurations";

    /** Row ID of the configuration record. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "config_row_id")
    public abstract long getConfigRowId();

    /**
     * Configuration type to which this configuration entry belongs to.
     *
     * <p>This config type will be one of the types in,
     * AdServices/adservices/service-core/proto/config-delivery/configuration_type.proto
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "type")
    public abstract int getType();

    /** Version of the configuration record. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "version")
    public abstract long getVersion();

    /**
     * Unique identifier to retrieve the configuration.
     *
     * <p>This value is unique within each configuration type and version.
     */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "id")
    public abstract String getId();

    /**
     * Configuration proto binary data (persisted as Blob).
     *
     * <p>This byte array will be parsed into its relevant proto type using Proto's parseFrom API.
     */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "value", typeAffinity = ColumnInfo.BLOB)
    @SuppressWarnings("mutable")
    public abstract byte[] getValue();

    /** Returns a {@link Builder} for {@link ConfigurationEntity} */
    public static Builder builder() {
        return new AutoValue_ConfigurationEntity.Builder();
    }

    /**
     * Room requires a 'create' method for AutoValue generated objects.
     *
     * <p>Creates and returns a {@link ConfigurationEntity} object.
     */
    public static ConfigurationEntity create(
            long configRowId, int type, long version, String id, byte[] value) {

        return builder()
                .setConfigRowId(configRowId)
                .setType(type)
                .setVersion(version)
                .setId(id)
                .setValue(value)
                .build();
    }

    /** Provides a builder to create an instance of {@link ConfigurationEntity} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getConfigRowId()} */
        public abstract Builder setConfigRowId(long configRowId);

        /** For more details see {@link #getType()} ()} */
        public abstract Builder setType(int type);

        /** For more details see {@link #getVersion()} */
        public abstract Builder setVersion(long version);

        /** For more details see {@link #getId()} */
        public abstract Builder setId(String id);

        /** For more details see {@link #getValue()} */
        public abstract Builder setValue(byte[] value);

        /**
         * @return an instance of {@link ConfigurationEntity}
         */
        public abstract ConfigurationEntity build();
    }
}
