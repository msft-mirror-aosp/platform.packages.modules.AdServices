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

package com.android.adservices.data.customaudience;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import com.google.auto.value.AutoValue;

@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = DBCustomAudienceToLeave.TABLE_NAME,
        foreignKeys =
                @ForeignKey(
                        entity = DBScheduledCustomAudienceUpdate.class,
                        parentColumns = {"update_id"},
                        childColumns = {"update_id"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"update_id", "name"},
                    unique = true)
        },
        primaryKeys = {"update_id", "name"})
public abstract class DBCustomAudienceToLeave {
    public static final String TABLE_NAME = "custom_audience_to_leave";

    /** Unique id associated with a delayed update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "update_id")
    @NonNull
    public abstract Long getUpdateId();

    /** Gets Custom Audience name */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "name")
    @NonNull
    public abstract String getName();

    /** Provides a {@link DBCustomAudience.Builder} */
    @NonNull
    public static DBCustomAudienceToLeave.Builder builder() {
        return new AutoValue_DBCustomAudienceToLeave.Builder();
    }

    /** Creates an instance of {@link DBCustomAudienceToLeave} */
    @NonNull
    public static DBCustomAudienceToLeave create(@NonNull Long updateId, @NonNull String name) {
        return builder().setUpdateId(updateId).setName(name).build();
    }

    /** Builder for convenient creation of an object of type {@link DBCustomAudienceToLeave} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** see {@link #getUpdateId()} */
        @NonNull
        public abstract Builder setUpdateId(@NonNull Long updateId);

        /** see {@link #getName()} */
        @NonNull
        public abstract Builder setName(@NonNull String name);

        /** Builds a {@link DBCustomAudience.Builder} */
        @NonNull
        public abstract DBCustomAudienceToLeave build();
    }
}
