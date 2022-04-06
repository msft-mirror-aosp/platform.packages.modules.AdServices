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

import java.util.Objects;

/** Room based database for Ad Selection. */
@Database(
        exportSchema = false,
        entities = {AdSelection.class, BuyerDecisionLogic.class},
        version = 1)
public abstract class AdSelectionDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "adservicesroom.db";

    private static AdSelectionDatabase sSingleton = null;

    /** Returns an instance of the AdSelectionDatabase given a context. */
    public static synchronized AdSelectionDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (Objects.isNull(sSingleton)) {
            sSingleton =
                    Room.databaseBuilder(context, AdSelectionDatabase.class, DATABASE_NAME).build();
        }
        return sSingleton;
    }
    /**
     * @return a Dao to access entities in AdSelection database.
     */
    public abstract AdSelectionEntryDao adSelectionEntryDao();
}
