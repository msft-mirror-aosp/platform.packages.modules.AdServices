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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * DAO abstract class used to access ProtectedSignal storage
 *
 * <p>Annotations will generate Room-based SQLite Dao impl.
 */
@Dao
public abstract class ProtectedSignalsDao {

    /**
     * Returns a list of all signals owned by the given buyer.
     *
     * @param buyer The buyer to retrieve signals for.
     * @return A list of all protected signals owned by the input buyer.
     */
    @Query("SELECT * FROM protected_signals WHERE buyer = :buyer")
    public abstract List<DBProtectedSignal> getSignalsByBuyer(AdTechIdentifier buyer);

    /**
     * Inserts signals into the database.
     *
     * @param signals The signals to insert.
     */
    @Insert
    public abstract void insertSignals(List<DBProtectedSignal> signals);

    /**
     * Deletes signals from the database.
     *
     * @param signals The signals to delete.
     */
    @Delete
    public abstract void deleteSignals(List<DBProtectedSignal> signals);
}
