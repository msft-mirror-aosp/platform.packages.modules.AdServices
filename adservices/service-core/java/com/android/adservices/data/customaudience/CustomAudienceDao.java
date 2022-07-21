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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * DAO abstract class used to access Custom Audience persistent storage.
 *
 * <p>Annotations will generate Room-based SQLite Dao impl.
 */
@Dao
public abstract class CustomAudienceDao {
    /**
     * Add user to a new custom audience. As designed, will override existing one.
     *
     * <p>This method is not meant to be used on its own, since custom audiences must be persisted
     * alongside matching background fetch data. Use {@link
     * #insertOrOverwriteCustomAudience(DBCustomAudience, Uri)} instead.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void persistCustomAudience(@NonNull DBCustomAudience customAudience);

    /**
     * Adds or updates background fetch data for a custom audience.
     *
     * <p>This method does not update the corresponding custom audience. Use {@link
     * #updateCustomAudienceAndBackgroundFetchData(DBCustomAudienceBackgroundFetchData,
     * CustomAudienceUpdatableData)} to do so safely.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistCustomAudienceBackgroundFetchData(
            @NonNull DBCustomAudienceBackgroundFetchData fetchData);

    /**
     * Adds or updates a given custom audience and background fetch data in a single transaction.
     *
     * <p>This transaction is separate in order to minimize the critical region while locking the
     * database. It is not meant to be exposed or used by itself; use {@link
     * #insertOrOverwriteCustomAudience(DBCustomAudience, Uri)} instead.
     */
    @Transaction
    protected void insertOrOverwriteCustomAudienceAndBackgroundFetchData(
            @NonNull DBCustomAudience customAudience,
            @NonNull DBCustomAudienceBackgroundFetchData fetchData) {
        persistCustomAudience(customAudience);
        persistCustomAudienceBackgroundFetchData(fetchData);
    }

    /**
     * Adds the user to the given custom audience.
     *
     * <p>If a custom audience already exists, it is overwritten completely.
     *
     * <p>Background fetch data is also created based on the given {@code customAudience} and {@code
     * dailyUpdateUrl} and overwrites any existing background fetch data. This method assumes the
     * input parameters have already been validated and are correct.
     */
    public void insertOrOverwriteCustomAudience(
            @NonNull DBCustomAudience customAudience, @NonNull Uri dailyUpdateUrl) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(dailyUpdateUrl);

        DBCustomAudienceBackgroundFetchData fetchData =
                DBCustomAudienceBackgroundFetchData.builder()
                        .setOwner(customAudience.getOwner())
                        .setBuyer(customAudience.getBuyer())
                        .setName(customAudience.getName())
                        .setDailyUpdateUrl(dailyUpdateUrl)
                        .setEligibleUpdateTime(
                                DBCustomAudienceBackgroundFetchData
                                        .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                                customAudience.getCreationTime()))
                        .build();

        insertOrOverwriteCustomAudienceAndBackgroundFetchData(customAudience, fetchData);
    }

    /**
     * Updates a custom audience and its background fetch data based on the given {@link
     * CustomAudienceUpdatableData} in a single transaction.
     *
     * <p>If no custom audience is found corresponding to the given {@link
     * DBCustomAudienceBackgroundFetchData}, no action is taken.
     */
    @Transaction
    public void updateCustomAudienceAndBackgroundFetchData(
            @NonNull DBCustomAudienceBackgroundFetchData fetchData,
            @NonNull CustomAudienceUpdatableData updatableData) {
        Objects.requireNonNull(fetchData);
        Objects.requireNonNull(updatableData);

        DBCustomAudience customAudience =
                getCustomAudienceByPrimaryKey(
                        fetchData.getOwner(), fetchData.getBuyer(), fetchData.getName());

        if (customAudience == null) {
            // This custom audience could have been cleaned up while it was being updated
            return;
        }

        customAudience = customAudience.copyWithUpdatableData(updatableData);

        persistCustomAudience(customAudience);
        persistCustomAudienceBackgroundFetchData(fetchData);
    }

    /** Get count of custom audience. */
    @Query("SELECT COUNT(*) FROM custom_audience")
    public abstract long getCustomAudienceCount();

    /** Get count of custom audience of a given owner. */
    @Query("SELECT COUNT(*) FROM custom_audience WHERE owner=:owner")
    public abstract long getCustomAudienceCountForOwner(String owner);

    /** Get the total number of distinct custom audience owner. */
    @Query("SELECT COUNT(DISTINCT owner) FROM custom_audience")
    public abstract long getCustomAudienceOwnerCount();

    /**
     * Get the count of total custom audience, the count for the given owner and the count of
     * distinct owner in one transaction.
     *
     * @param owner the owner we need check the count against.
     * @return the aggregated data of custom audience count
     */
    @Transaction
    @NonNull
    public CustomAudienceStats getCustomAudienceStats(@NonNull String owner) {
        Objects.requireNonNull(owner);

        long customAudienceCount = getCustomAudienceCount();
        long customAudienceCountPerOwner = getCustomAudienceCountForOwner(owner);
        long ownerCount = getCustomAudienceOwnerCount();
        return new CustomAudienceStats(
                owner, customAudienceCount, customAudienceCountPerOwner, ownerCount);
    }

    /**
     * Add a custom audience override into the table custom_audience_overrides
     *
     * @param customAudienceOverride is the CustomAudienceOverride to add to table
     *     custom_audience_overrides. If a {@link DBCustomAudienceOverride} object with the primary
     *     key already exists, this will replace the existing object.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void persistCustomAudienceOverride(
            DBCustomAudienceOverride customAudienceOverride);

    /**
     * Checks if there is a row in the custom audience override data with the unique key combination
     * of owner, buyer, and name
     *
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM custom_audience_overrides WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name LIMIT 1)")
    public abstract boolean doesCustomAudienceOverrideExist(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Get custom audience by its unique key.
     *
     * @return custom audience result if exists.
     */
    @Query("SELECT * FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    public abstract DBCustomAudience getCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Get custom audience background fetch data by its unique key.
     *
     * @return custom audience background fetch data if it exists
     */
    @Query(
            "SELECT * FROM custom_audience_background_fetch_data "
                    + "WHERE owner = :owner AND buyer = :buyer AND name = :name")
    @Nullable
    @VisibleForTesting
    public abstract DBCustomAudienceBackgroundFetchData
            getCustomAudienceBackgroundFetchDataByPrimaryKey(
                    @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Get custom audience JS override by its unique key.
     *
     * @return custom audience override result if exists.
     */
    @Query(
            "SELECT bidding_logic FROM custom_audience_overrides WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name AND app_package_name= :appPackageName")
    @Nullable
    @VisibleForTesting
    public abstract String getBiddingLogicUrlOverride(
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
            "SELECT trusted_bidding_data FROM custom_audience_overrides WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name AND app_package_name= :appPackageName")
    @Nullable
    @VisibleForTesting
    public abstract String getTrustedBiddingDataOverride(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /** Delete the custom audience given owner, buyer, and name. */
    @Query("DELETE FROM custom_audience WHERE owner = :owner AND buyer = :buyer AND name = :name")
    protected abstract void deleteCustomAudienceByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /** Delete background fetch data for the custom audience given owner, buyer, and name. */
    @Query(
            "DELETE FROM custom_audience_background_fetch_data WHERE owner = :owner "
                    + "AND buyer = :buyer AND name = :name")
    protected abstract void deleteCustomAudienceBackgroundFetchDataByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name);

    /**
     * Delete all custom audience data corresponding to the given {@code owner}, {@code buyer}, and
     * {@code name} in a single transaction.
     */
    @Transaction
    public void deleteAllCustomAudienceDataByPrimaryKey(
            @NonNull String owner, @NonNull String buyer, @NonNull String name) {
        deleteCustomAudienceByPrimaryKey(owner, buyer, name);
        deleteCustomAudienceBackgroundFetchDataByPrimaryKey(owner, buyer, name);
    }

    /**
     * Deletes all custom audiences which are expired, where the custom audiences' expiration times
     * match or precede the given {@code expiryTime}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredCustomAudienceData(Instant)} instead.
     *
     * @return the number of deleted custom audiences
     */
    @Query("DELETE FROM custom_audience WHERE expiration_time <= :expiryTime")
    protected abstract int deleteAllExpiredCustomAudiences(@NonNull Instant expiryTime);

    /**
     * Deletes background fetch data for all custom audiences which are expired, where the custom
     * audiences' expiration times match or precede the given {@code expiryTime}.
     *
     * <p>This method is not intended to be called on its own. Please use {@link
     * #deleteAllExpiredCustomAudienceData(Instant)} instead.
     */
    @Query(
            "DELETE FROM custom_audience_background_fetch_data WHERE ROWID IN "
                    + "(SELECT bgf.ROWID FROM custom_audience_background_fetch_data AS bgf "
                    + "INNER JOIN custom_audience AS ca "
                    + "ON bgf.buyer = ca.buyer AND bgf.owner = ca.owner AND bgf.name = ca.name "
                    + "WHERE expiration_time <= :expiryTime)")
    protected abstract void deleteAllExpiredCustomAudienceBackgroundFetchData(
            @NonNull Instant expiryTime);

    /**
     * Deletes all expired custom audience data in a single transaction, where the custom audiences'
     * expiration times match or precede the given {@code expiryTime}.
     *
     * @return the number of deleted custom audiences
     */
    @Transaction
    public int deleteAllExpiredCustomAudienceData(@NonNull Instant expiryTime) {
        deleteAllExpiredCustomAudienceBackgroundFetchData(expiryTime);
        return deleteAllExpiredCustomAudiences(expiryTime);
    }

    /** Clean up selected custom audience override data by its primary key */
    @Query(
            "DELETE FROM custom_audience_overrides WHERE owner = :owner AND buyer = :buyer "
                    + "AND name = :name AND app_package_name = :appPackageName")
    public abstract void removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull String appPackageName);

    /** Clean up all custom audience override data */
    @Query("DELETE FROM custom_audience_overrides WHERE app_package_name = :appPackageName")
    public abstract void removeAllCustomAudienceOverrides(@NonNull String appPackageName);

    /**
     * Fetch all the Custom Audience corresponding to the buyers
     *
     * @param buyers associated with the Custom Audience
     * @param currentTime to compare against CA time values and find an active CA
     * @return All the Custom Audience that represent given buyers
     */
    // TODO(229297645): replace the validation check with last update time within 48 hours with a
    // value that is passed in by a P/H flag.
    @Query(
            "SELECT * FROM custom_audience "
                    + "WHERE buyer in (:buyers) "
                    + "AND activation_time <= (:currentTime) "
                    + "AND (:currentTime) < expiration_time "
                    + "AND (last_ads_and_bidding_data_updated_time + 48 * 3600000) "
                    + ">= (:currentTime) "
                    + "AND user_bidding_signals IS NOT NULL "
                    + "AND trusted_bidding_data_url IS NOT NULL "
                    + "AND ads IS NOT NULL ")
    @Nullable
    public abstract List<DBCustomAudience> getActiveCustomAudienceByBuyers(
            List<String> buyers, Instant currentTime);

    /**
     * Gets all {@link DBCustomAudienceBackgroundFetchData} for custom audiences that are active,
     * not expired, and eligible for update.
     */
    @Query(
            "SELECT bgf.* FROM custom_audience_background_fetch_data AS bgf "
                    + "INNER JOIN custom_audience AS ca "
                    + "ON bgf.buyer = ca.buyer AND bgf.owner = ca.owner AND bgf.name = ca.name "
                    + "WHERE bgf.eligible_update_time <= :currentTime "
                    + "AND ca.activation_time <= :currentTime "
                    + "AND :currentTime < ca.expiration_time "
                    + "ORDER BY ca.last_ads_and_bidding_data_updated_time ASC "
                    + "LIMIT :maxRowsReturned")
    @NonNull
    public abstract List<DBCustomAudienceBackgroundFetchData>
            getActiveEligibleCustomAudienceBackgroundFetchData(
                    @NonNull Instant currentTime, long maxRowsReturned);

    /** Class represents custom audience stats query result. */
    public static class CustomAudienceStats {
        private final String mOwner;
        private final long mTotalCount;
        private final long mPerOwnerCount;
        private final long mOwnerCount;

        public CustomAudienceStats(
                String owner, long totalCount, long perOwnerCount, long ownerCount) {
            mOwner = owner;
            mTotalCount = totalCount;
            mPerOwnerCount = perOwnerCount;
            mOwnerCount = ownerCount;
        }

        public String getOwner() {
            return mOwner;
        }

        public long getTotalCount() {
            return mTotalCount;
        }

        public long getPerOwnerCount() {
            return mPerOwnerCount;
        }

        public long getOwnerCount() {
            return mOwnerCount;
        }
    }
}
