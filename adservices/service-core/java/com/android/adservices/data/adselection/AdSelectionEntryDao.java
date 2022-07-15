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

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.android.internal.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.List;

/**
 * Data Access Object interface for access to the local AdSelection data storage.
 *
 * <p>Annotation will generate Room based SQLite Dao implementation. TODO(b/228114258) Add unit
 * tests
 */
// TODO (b/229660121): Ad unit tests for this class
@Dao
public interface AdSelectionEntryDao {
    /**
     * Add a new successful ad selection entry into the table ad_selection.
     *
     * @param adSelection is the AdSelection to add to the table ad_selection if the ad_selection_id
     *     not exists.
     */
    // TODO(b/230568647): retry adSelectionId generation in case of collision
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void persistAdSelection(DBAdSelection adSelection);

    /**
     * Write a buyer decision logic entry into the table buyer_decision_logic.
     *
     * @param buyerDecisionLogic is the BuyerDecisionLogic to write to table buyer_decision_logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void persistBuyerDecisionLogic(DBBuyerDecisionLogic buyerDecisionLogic);

    /**
     * Add an ad selection override into the table ad_selection_overrides
     *
     * @param adSelectionOverride is the AdSelectionOverride to add to table ad_selection_overrides.
     *     If a {@link DBAdSelectionOverride} object with the {@code adSelectionConfigId} already
     *     exists, this will replace the existing object.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void persistAdSelectionOverride(DBAdSelectionOverride adSelectionOverride);

    /**
     * Checks if there is a row in the ad selection data with the unique key ad_selection_id
     *
     * @param adSelectionId which is the key to query the corresponding ad selection data.
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM ad_selection WHERE ad_selection_id = :adSelectionId LIMIT"
                    + " 1)")
    boolean doesAdSelectionIdExist(long adSelectionId);

    /**
     * Checks if there is a row in the ad selection override data with the unique key
     * ad_selection_config_id
     *
     * @param adSelectionConfigId which is the key to query the corresponding ad selection override
     *     data.
     * @return true if row exists, false otherwise
     */
    @Query(
            "SELECT EXISTS(SELECT 1 FROM ad_selection_overrides WHERE ad_selection_config_id ="
                    + " :adSelectionConfigId AND app_package_name = :appPackageName LIMIT 1)")
    boolean doesAdSelectionOverrideExistForPackageName(
            String adSelectionConfigId, String appPackageName);

    /**
     * Get the ad selection entry by its unique key ad_selection_id.
     *
     * @param adSelectionId which is the key to query the corresponding ad selection entry.
     * @return an {@link DBAdSelectionEntry} if exists.
     */
    @Query(
            "SELECT ad_selection.ad_selection_id as ad_selection_id,"
                + " ad_selection.custom_audience_signals_owner as custom_audience_signals_owner,"
                + " ad_selection.custom_audience_signals_buyer as custom_audience_signals_buyer,"
                + " ad_selection.custom_audience_signals_name as custom_audience_signals_name,"
                + " ad_selection.custom_audience_signals_activation_time as"
                + " custom_audience_signals_activation_time,"
                + " ad_selection.custom_audience_signals_expiration_time as"
                + " custom_audience_signals_expiration_time,"
                + " ad_selection.custom_audience_signals_user_bidding_signals as"
                + " custom_audience_signals_user_bidding_signals, ad_selection.contextual_signals"
                + " as contextual_signals,ad_selection.winning_ad_render_uri as"
                + " winning_ad_render_uri,ad_selection.winning_ad_bid as"
                + " winning_ad_bid,ad_selection.creation_timestamp as"
                + " creation_timestamp,buyer_decision_logic.buyer_decision_logic_js as"
                + " buyer_decision_logic_js FROM ad_selection LEFT JOIN buyer_decision_logic ON"
                + " ad_selection.bidding_logic_uri = buyer_decision_logic.bidding_logic_uri WHERE"
                + " ad_selection.ad_selection_id = :adSelectionId")
    DBAdSelectionEntry getAdSelectionEntityById(long adSelectionId);

    /**
     * Get the ad selection entries with a batch of ad_selection_ids.
     *
     * @param adSelectionIds are the list of keys to query the corresponding ad selection entries.
     * @return ad selection entries if exists.
     */
    @Query(
            "SELECT ad_selection.ad_selection_id AS"
                + " ad_selection_id,ad_selection.custom_audience_signals_owner as"
                + " custom_audience_signals_owner, ad_selection.custom_audience_signals_buyer as"
                + " custom_audience_signals_buyer, ad_selection.custom_audience_signals_name as"
                + " custom_audience_signals_name,"
                + " ad_selection.custom_audience_signals_activation_time as"
                + " custom_audience_signals_activation_time,"
                + " ad_selection.custom_audience_signals_expiration_time as"
                + " custom_audience_signals_expiration_time,"
                + " ad_selection.custom_audience_signals_user_bidding_signals as"
                + " custom_audience_signals_user_bidding_signals, ad_selection.contextual_signals"
                + " AS contextual_signals,ad_selection.winning_ad_render_uri AS"
                + " winning_ad_render_uri,ad_selection.winning_ad_bid AS winning_ad_bid,"
                + " ad_selection.creation_timestamp as creation_timestamp,"
                + " buyer_decision_logic.buyer_decision_logic_js AS buyer_decision_logic_js FROM"
                + " ad_selection LEFT JOIN buyer_decision_logic ON ad_selection.bidding_logic_uri"
                + " = buyer_decision_logic.bidding_logic_uri WHERE ad_selection.ad_selection_id IN"
                + " (:adSelectionIds) ")
    List<DBAdSelectionEntry> getAdSelectionEntities(List<Long> adSelectionIds);

    /**
     * Get ad selection JS override by its unique key and the package name of the app that created
     * the override.
     *
     * @return ad selection override result if exists.
     */
    @Query(
            "SELECT decision_logic FROM ad_selection_overrides WHERE ad_selection_config_id ="
                    + " :adSelectionConfigId AND app_package_name = :appPackageName")
    @Nullable
    @VisibleForTesting
    String getDecisionLogicOverride(String adSelectionConfigId, String appPackageName);

    /**
     * Get ad selection trusted scoring signals override by its unique key and the package name of
     * the app that created the override.
     *
     * @return ad selection override result if exists.
     */
    @Query(
            "SELECT trusted_scoring_signals FROM ad_selection_overrides WHERE"
                    + " ad_selection_config_id = :adSelectionConfigId AND app_package_name ="
                    + " :appPackageName")
    @Nullable
    @VisibleForTesting
    String getTrustedScoringSignalsOverride(String adSelectionConfigId, String appPackageName);

    /**
     * Clean up expired adSelection entries if it is older than the given timestamp. If
     * creation_timestamp < expirationTime, the ad selection entry will be removed from the
     * ad_selection table.
     *
     * @param expirationTime is the cutoff time to expire the AdSelectionEntry.
     */
    @Query("DELETE FROM ad_selection WHERE creation_timestamp < :expirationTime")
    void removeExpiredAdSelection(Instant expirationTime);

    /**
     * Clean up selected ad selection data entry data in batch by their ad_selection_ids.
     *
     * @param adSelectionIds is the list of adSelectionIds to identify the data entries to be
     *     removed from ad_selection and buyer_decision_logic tables.
     */
    @Query("DELETE FROM ad_selection WHERE ad_selection_id IN (:adSelectionIds)")
    void removeAdSelectionEntriesByIds(List<Long> adSelectionIds);

    /**
     * Clean up selected ad selection override data by its {@code adSelectionConfigId}
     *
     * @param adSelectionConfigId is the {@code adSelectionConfigId} to identify the data entries to
     *     be removed from the ad_selection_overrides table.
     */
    @Query(
            "DELETE FROM ad_selection_overrides WHERE ad_selection_config_id = :adSelectionConfigId"
                    + " AND app_package_name = :appPackageName")
    void removeAdSelectionOverrideByIdAndPackageName(
            String adSelectionConfigId, String appPackageName);

    /**
     * Clean up buyer_decision_logic entries in batch if the bidding_logic_uri no longer exists in
     * the table ad_selection.
     */
    @Query(
            "DELETE FROM buyer_decision_logic WHERE bidding_logic_uri NOT IN "
                    + "( SELECT DISTINCT bidding_logic_uri "
                    + "FROM ad_selection "
                    + "WHERE bidding_logic_uri is NOT NULL)")
    void removeExpiredBuyerDecisionLogic();

    /** Clean up all ad selection override data */
    @Query("DELETE FROM ad_selection_overrides WHERE  app_package_name = :appPackageName")
    void removeAllAdSelectionOverrides(String appPackageName);
}
