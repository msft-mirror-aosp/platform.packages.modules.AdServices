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

import java.util.List;

/**
 * Dao interface to access Custom Audience storage.
 *
 * <p>Annotations will generate Room based SQLite Dao impl.
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
     * Add a custom audience override into the table custom_audience_overrides
     *
     * @param customAudienceOverride is the CustomAudienceOverride to add to table
     *     custom_audience_overrides. If a {@link DBCustomAudienceOverride} object with the primary
     *     key already exists, this will replace the existing object.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void persistCustomAudienceOverride(DBCustomAudienceOverride customAudienceOverride);

    /**
     * Checks if there is a row in the custom audience override data with the unique key combination
     * of owner, buyer, and name
     *
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM custom_audience_overrides WHERE owner = :owner AND buyer ="
                    + " :buyer AND name = :name LIMIT 1)")
    boolean doesCustomAudienceOverrideExist(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Get custom audience by its unique key.
     *
     * @return custom audience result if exists.
     */
    @Query("SELECT * FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    DBCustomAudience getCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Get custom audience JS override by its unique key.
     *
     * @return custom audience override result if exists.
     */
    @Query(
            "SELECT bidding_logic FROM custom_audience_overrides WHERE owner = :owner AND buyer ="
                    + " :buyer AND name = :name AND app_package_name= :appPackageName")
    @Nullable
    @VisibleForTesting
    String getBiddingLogicUrlOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /**
     * Get trusted bidding data override by its unique key.
     *
     * @return custom audience override result if exists.
     */
    @Query(
            "SELECT trusted_bidding_data FROM custom_audience_overrides WHERE owner = :owner AND"
                    + " buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    String getTrustedBiddingDataOverride(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /** Delete the custom audience given owner, buyer, and name. */
    @Query("DELETE FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    void deleteCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /** Clean up selected custom audience override data by its primary key */
    @Query(
            "DELETE FROM custom_audience_overrides WHERE owner = :owner AND buyer = :buyer AND name"
                    + " = :name AND app_package_name = :appPackageName")
    void removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /** Clean up all custom audience override data */
    @Query("DELETE FROM custom_audience_overrides WHERE app_package_name = :appPackageName")
    void removeAllCustomAudienceOverrides(@NonNull String appPackageName);

    /**
     * Fetch all the Custom Audience corresponding to the buyers
     *
     * @param buyers associated with the Custom Audience
     * @return All the Custom Audience that represent given buyers
     */
    @Query("SELECT * FROM custom_audience where buyer in (:buyers)")
    @Nullable
    List<DBCustomAudience> getCustomAudienceByBuyers(List<String> buyers);
}
