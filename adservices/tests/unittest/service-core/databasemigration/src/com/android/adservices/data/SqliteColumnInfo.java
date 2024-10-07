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

package com.android.adservices.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

/** SQLite table schema extracted by pragma_table_info. */
@AutoValue
abstract class SqliteColumnInfo {
    /** Provides a {@link SqliteColumnInfo.Builder} */
    @NonNull
    static SqliteColumnInfo.Builder builder() {
        return new AutoValue_SqliteColumnInfo.Builder();
    }

    /** Returns its name */
    @NonNull
    abstract String getName();

    /** Returns data type if given, else '' */
    @NonNull
    abstract String getType();

    /** Returns whether or not the column can be NULL */
    abstract boolean isNotnull();

    /** Returns the default value for the column */
    @Nullable
    abstract Object getDefaultValue();

    /**
     * Returns either zero for columns that are not part of the primary key, or the 1-based index of
     * the column within the primary key
     */
    abstract int getPrimaryKey();

    boolean equalsWithoutDefaultValue(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqliteColumnInfo)) return false;
        SqliteColumnInfo that = (SqliteColumnInfo) o;
        return this.getName().equals(that.getName())
                && this.getType().equals(that.getType())
                && this.getPrimaryKey() == that.getPrimaryKey()
                && this.isNotnull() == that.isNotnull();
    }

    @AutoValue.Builder
    abstract static class Builder {
        /** Sets its name */
        @NonNull
        abstract Builder setName(@NonNull String name);

        /** Sets data type if given, else '' */
        @NonNull
        abstract Builder setType(@NonNull String type);

        /** Sets whether or not the column can be NULL */
        @NonNull
        abstract Builder setNotnull(boolean notNull);

        /** Sets the default value for the column */
        @NonNull
        abstract Builder setDefaultValue(@Nullable Object defaultValue);

        /**
         * Sets either zero for columns that are not part of the primary key, or the 1-based index
         * of the column within the primary key
         */
        @NonNull
        abstract Builder setPrimaryKey(int primaryKey);

        /** Builds a {@link SqliteColumnInfo}. */
        @NonNull
        abstract SqliteColumnInfo build();
    }
}
