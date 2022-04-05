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

package com.android.adservices.data;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.internal.annotations.GuardedBy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Room based PP API database.
 */
@Database(
        // Set exportSchema to true to see generated schema file.
        // File location is defined in Android.bp -Aroom.schemaLocation.
        exportSchema = false,
        entities = {DBCustomAudience.class},
        version = AdServicesDatabase.DATABASE_VERSION
)
@TypeConverters({AdServicesDatabase.Converters.class})
public abstract class AdServicesDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "adservices.db";

    @GuardedBy("SINGLETON_LOCK")
    private static AdServicesDatabase sSingleton;

    // TODO: How we want handle synchronized situation (b/228101878).
    /** Returns an instance of the AdServiceDatabase given a context. */
    public static AdServicesDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = Room.databaseBuilder(context, AdServicesDatabase.class, DATABASE_NAME)
                        .build();
            }
            return sSingleton;
        }
    }

    /**
     * Custom Audience Dao.
     *
     * @return Dao to access custom audience storage.
     */
    public abstract CustomAudienceDao customAudienceDao();

    /**
     * Room DB type converters.
     * Register custom type converters here.
     */
    public static class Converters {

        private Converters() {
        }

        /**
         * Serialize {@link Instant} to Long.
         */
        @TypeConverter
        @Nullable
        public static Long serializeInstant(@Nullable Instant instant) {
            return Optional.ofNullable(instant)
                    .map(Instant::toEpochMilli)
                    .orElse(null);
        }

        /**
         * Deserialize {@link Instant} from long.
         */
        @TypeConverter
        @Nullable
        public static Instant deserializeInstant(@Nullable Long epochMilli) {
            return Optional.ofNullable(epochMilli)
                    .map(Instant::ofEpochMilli)
                    .orElse(null);
        }

        /**
         * Deserialize {@link Uri} from String.
         */
        @TypeConverter
        @Nullable
        public static Uri deserializeUrl(@Nullable String uri) {
            return Optional.ofNullable(uri)
                    .map(Uri::parse)
                    .orElse(null);
        }

        /**
         * Serialize {@link Uri} to String.
         */
        @TypeConverter
        @Nullable
        public static String serializeUrl(@Nullable Uri uri) {
            return Optional.ofNullable(uri)
                    .map(Uri::toString)
                    .orElse(null);
        }
    }
}
