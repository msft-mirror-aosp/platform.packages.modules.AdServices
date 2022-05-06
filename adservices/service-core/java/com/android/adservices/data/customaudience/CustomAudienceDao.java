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

package com.android.adservices.data.customaudience;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Dao interface to access Custom Audience storage.
 *
 * Annotations will generate Room based SQLite Dao impl.
 */
@Dao
public interface CustomAudienceDao {
    /**
     * Add user to a new custom audience. As designed, will override existing one.
     *
     * @param customAudience the custom audience to add.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrOverrideCustomAudience(@NonNull DBCustomAudience customAudience);

    /**
     * Get custom audience by its unique key.
     *
     * @return custom audience result if exists.
     */
    @Query("SELECT * FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    DBCustomAudience getCustomAudienceByPrimaryKey(@NonNull String owner, @NonNull String buyer,
            @NonNull String name);

    /**
     * Delete the custom audience given owner, buyer, and name.
     */
    @Query("DELETE FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    void deleteCustomAudienceByPrimaryKey(@NonNull String owner, @NonNull String buyer,
            @NonNull String name);
}
