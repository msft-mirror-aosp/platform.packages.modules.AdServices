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

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/**
 * Data Access Object interface for access to the local AdSelection data storage.
 *
 * <p>Annotation will generate Room based SQLite Dao implementation.
 * TODO(b/228114258) Add unit tests
 */
@Dao
public interface AdSelectionEntryDao {
    /**
     * Add a new successful ad selection entry into the table ad_selection.
     *
     * @param adSelection is the AdSelection to add to the table ad_selection if the ad_selection_id
     *                    not exists.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void persistAdSelection(DBAdSelection adSelection);

    /**
     * Add a buyer decision logic entry into the table buyer_decision_logic.
     *
     * @param buyerDecisionLogic is the BuyerDecisionLogic to add to table buyer_decision_logic if
     *                           not exists.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void persistBuyerDecisionLogic(DBBuyerDecisionLogic buyerDecisionLogic);

    /**
     * Get the ad selection entry by its unique key ad_selection_id.
     *
     * @param adSelectionId which is the key to query the corresponding ad selection entry.
     * @return an {@link DBAdSelectionEntry} if exists.
     */
    @Query(
            "SELECT "
                    + "ad_selection.ad_selection_id as ad_selection_id, "
                    + "ad_selection.custom_audience_signals as custom_audience_signals,"
                    + "ad_selection.contextual_signals as contextual_signals,"
                    + "ad_selection.winning_ad_render_url as winning_ad_render_url,"
                    + "ad_selection.winning_ad_bid as winning_ad_bid,"
                    + "ad_selection.creation_timestamp as creation_timestamp,"
                    + "buyer_decision_logic.buyer_decision_logic_js as buyer_decision_logic_js "
                    + "FROM ad_selection "
                    + "LEFT JOIN "
                    + "buyer_decision_logic "
                    + "ON ad_selection.bidding_logic_url = buyer_decision_logic.bidding_logic_url "
                    + "WHERE ad_selection.ad_selection_id = :adSelectionId")
    DBAdSelectionEntry getAdSelectionEntityById(long adSelectionId);

    /**
     * Get the ad selection entries with a batch of ad_selection_ids.
     *
     * @param adSelectionIds are the list of keys to query the corresponding ad selection entries.
     * @return ad selection entries if exists.
     */
    @Query(
            "SELECT ad_selection.ad_selection_id AS ad_selection_id,"
                    + "ad_selection.custom_audience_signals AS custom_audience_signals,"
                    + "ad_selection.contextual_signals AS contextual_signals,"
                    + "ad_selection.winning_ad_render_url AS winning_ad_render_url,"
                    + "ad_selection.winning_ad_bid AS winning_ad_bid, "
                    + "ad_selection.creation_timestamp as creation_timestamp, "
                    + "buyer_decision_logic.buyer_decision_logic_js AS buyer_decision_logic_js "
                    + "FROM ad_selection LEFT JOIN buyer_decision_logic "
                    + "ON ad_selection.bidding_logic_url = buyer_decision_logic.bidding_logic_url "
                    + "WHERE ad_selection.ad_selection_id IN (:adSelectionIds) ")
    List<DBAdSelectionEntry> getAdSelectionEntities(List<Long> adSelectionIds);

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
     *                       removed from ad_selection and buyer_decision_logic tables.
     */
    @Query("DELETE FROM ad_selection WHERE ad_selection_id IN (:adSelectionIds)")
    void removeAdSelectionEntriesByIds(List<Long> adSelectionIds);

    /**
     * Clean up buyer_decision_logic entries in batch if the bidding_logic_url no longer exists in
     * the table ad_selection.
     */
    @Query(
            "DELETE FROM buyer_decision_logic WHERE bidding_logic_url NOT IN "
                    + "( SELECT DISTINCT bidding_logic_url "
                    + "FROM ad_selection "
                    + "WHERE bidding_logic_url is NOT NULL)")
    void removeExpiredBuyerDecisionLogic();
}
