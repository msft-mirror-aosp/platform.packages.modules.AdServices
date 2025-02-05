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

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.errorprone.annotations.concurrent.GuardedBy;

@Database(
        entities = {ConfigurationEntity.class, LabelEntity.class},
        version = ConfigurationDatabase.DATABASE_VERSION)
public abstract class ConfigurationDatabase extends RoomDatabase {

    private static final Object SINGLETON_LOCK = new Object();
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("configuration.db");

    @GuardedBy("SINGLETON_LOCK")
    private static volatile ConfigurationDatabase sInstance;

    /** Returns an instance of the ConfigurationDatabase. */
    public static ConfigurationDatabase getInstance() {
        // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition.
        // Author states it provided 1.4x performance improvement.
        @SuppressWarnings("GuardedBy") // Lint is not smart enough to understand the optimization.
        ConfigurationDatabase singleReadResult = sInstance;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sInstance == null) {
                sInstance =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        ApplicationContextSingleton.get(),
                                        ConfigurationDatabase.class,
                                        DATABASE_NAME)
                                .build();
            }
            return sInstance;
        }
    }

    /**
     * @return a Dao to access entities in configuration database.
     */
    public abstract ConfigurationDao configurationDao();
}
