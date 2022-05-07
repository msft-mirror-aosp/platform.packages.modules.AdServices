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

package com.android.adservices.data.adselection;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.android.internal.annotations.GuardedBy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Room based database for Ad Selection. */
// TODO (b/229660121): Ad unit tests for this class
@Database(
        exportSchema = false,
        entities = {DBAdSelection.class, DBBuyerDecisionLogic.class, DBAdSelectionOverride.class},
        version = 1)
@TypeConverters({AdSelectionDatabase.Converters.class})
public abstract class AdSelectionDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final String DATABASE_NAME = "adservicesroom.db";

    @GuardedBy("SINGLETON_LOCK")
    private static AdSelectionDatabase sSingleton = null;

    /** Returns an instance of the AdSelectionDatabase given a context. */
    public static AdSelectionDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        synchronized (SINGLETON_LOCK) {
            if (Objects.isNull(sSingleton)) {
                sSingleton =
                        Room.databaseBuilder(context, AdSelectionDatabase.class, DATABASE_NAME)
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * @return a Dao to access entities in AdSelection database.
     */
    public abstract AdSelectionEntryDao adSelectionEntryDao();

    /** Room DB type converters. Register custom type converters here. */
    public static class Converters {

        private Converters() {}

        /** Serialize {@link Instant} to Long. */
        @TypeConverter
        @Nullable
        public static Long serializeInstant(@Nullable Instant instant) {
            return Optional.ofNullable(instant).map(Instant::toEpochMilli).orElse(null);
        }

        /** Deserialize {@link Instant} from long. */
        @TypeConverter
        @Nullable
        public static Instant deserializeInstant(@Nullable Long epochMilli) {
            return Optional.ofNullable(epochMilli).map(Instant::ofEpochMilli).orElse(null);
        }

        /** Deserialize {@link Uri} from String. */
        @TypeConverter
        @Nullable
        public static Uri deserializeUrl(@Nullable String uri) {
            return Optional.ofNullable(uri).map(Uri::parse).orElse(null);
        }

        /** Serialize {@link Uri} to String. */
        @TypeConverter
        @Nullable
        public static String serializeUrl(@Nullable Uri uri) {
            return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
        }
    }
}
