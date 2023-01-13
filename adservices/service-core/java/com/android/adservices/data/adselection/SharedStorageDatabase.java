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
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import java.util.Objects;

/** Room based database for cross-app data that is read from during ad selection */
@Database(
        entities = {
            DBAppInstallPermissions.class,
        },
        version = SharedStorageDatabase.DATABASE_VERSION)
@TypeConverters({FledgeRoomConverters.class})
public abstract class SharedStorageDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "sharedstorage.db";

    private static volatile SharedStorageDatabase sSingleton = null;

    /** Returns or creates the instance of SharedStorageDatabase given a context. */
    public static SharedStorageDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition
        SharedStorageDatabase singleReadResult = sSingleton;
        if (singleReadResult != null) {
            return singleReadResult;
        }
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        Room.databaseBuilder(context, SharedStorageDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /** @return a Dao to run queries for app install */
    public abstract AppInstallDao appInstallDao();
}
