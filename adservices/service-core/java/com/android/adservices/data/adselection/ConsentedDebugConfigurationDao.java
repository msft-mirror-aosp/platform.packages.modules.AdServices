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

package com.android.adservices.data.adselection;

import android.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.Instant;
import java.util.List;

/** Data Access Object interface for access to the local AdSelectionDebugReport data storage. */
@Dao
public abstract class ConsentedDebugConfigurationDao {

    /**
     * Persists consented debug configuration in {@link DBConsentedDebugConfiguration.TABLE_NAME}
     *
     * @param dbConsentedDebugConfiguration is {@link DBConsentedDebugConfiguration} to add to the
     *     table consented_debug_configuration.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistConsentedDebugConfiguration(
            @NonNull DBConsentedDebugConfiguration dbConsentedDebugConfiguration);

    /**
     * Fetch the most recently created consented debug configurations whose expiry timestamp is
     * after the given time.
     *
     * @param currentTime to compare against consented debug configuration expiry time
     * @param limit to specify how many consented debug configurations should be selected from DB.
     * @return All the consented debug configurations
     */
    @Query(
            "SELECT * FROM consented_debug_configuration "
                    + "WHERE expiry_timestamp > (:currentTime) "
                    + "AND is_consent_provided = 1 "
                    + "ORDER BY creation_timestamp desc "
                    + "LIMIT (:limit);")
    @Nullable
    public abstract List<DBConsentedDebugConfiguration> getAllActiveConsentedDebugConfigurations(
            @NonNull Instant currentTime, int limit);

    /** deletes all consented debug configurations. */
    @Query("DELETE FROM consented_debug_configuration")
    public abstract void deleteAllConsentedDebugConfigurations();

    /**
     * Deletes all existing {@link DBConsentedDebugConfiguration} and persist {@link
     * DBConsentedDebugConfiguration}
     *
     * @param dbConsentedDebugConfiguration is {@link DBConsentedDebugConfiguration} to add to the
     *     table consented_debug_configuration.
     */
    @Transaction
    public void deleteExistingConsentedDebugConfigurationsAndPersist(
            @NonNull DBConsentedDebugConfiguration dbConsentedDebugConfiguration) {
        deleteAllConsentedDebugConfigurations();
        persistConsentedDebugConfiguration(dbConsentedDebugConfiguration);
    }
}
