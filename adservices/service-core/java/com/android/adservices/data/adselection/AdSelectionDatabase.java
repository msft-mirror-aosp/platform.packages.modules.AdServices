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

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.internal.annotations.GuardedBy;

import java.util.Objects;

/** Room based database for Ad Selection. */
@Database(
        entities = {
            DBAdSelection.class,
            DBBuyerDecisionLogic.class,
            DBAdSelectionOverride.class,
            DBAdSelectionFromOutcomesOverride.class,
            DBRegisteredAdEvent.class
        },
        version = AdSelectionDatabase.DATABASE_VERSION,
        autoMigrations = {@AutoMigration(from = 1, to = 2), @AutoMigration(from = 2, to = 3)})
@TypeConverters({FledgeRoomConverters.class})
public abstract class AdSelectionDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 3;
    // TODO(b/230653780): Should we separate the DB.
    public static final String DATABASE_NAME = "adselection.db";

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
}
