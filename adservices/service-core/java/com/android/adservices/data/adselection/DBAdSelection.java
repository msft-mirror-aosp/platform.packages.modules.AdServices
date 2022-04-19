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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.android.internal.util.Preconditions;

import java.time.Instant;
import java.util.Objects;

/**
 * This POJO represents the AdSelection data in the ad_selection table entity. TODO(b/228114258):
 * Add foreign key on the bidding_logic_url column to enforce the mapping between AdSelection and
 * BuyerDecisionLogic, so that entries in the buyer_decision_logic table will not be deleted as long
 * as there is mapping exists in the ad_selection table.
 *
 * @hide
 */
// TODO (b/229660121): Ad unit tests for this class
@Entity(
        tableName = "ad_selection",
        indices = {@Index(value = {"bidding_logic_url"})})
public final class DBAdSelection {
    @ColumnInfo(name = "ad_selection_id")
    @PrimaryKey
    private final long mAdSelectionId;

    @Embedded(prefix = "custom_audience_signals_")
    @Nullable
    private final CustomAudienceSignals mCustomAudienceSignals;

    @ColumnInfo(name = "contextual_signals")
    @NonNull
    private final String mContextualSignals;

    @ColumnInfo(name = "bidding_logic_url")
    @Nullable
    private final Uri mBiddingLogicUrl;

    @ColumnInfo(name = "winning_ad_render_url")
    @NonNull
    private final Uri mWinningAdRenderUrl;

    @ColumnInfo(name = "winning_ad_bid")
    private final double mWinningAdBid;

    @ColumnInfo(name = "creation_timestamp")
    @NonNull
    private final Instant mCreationTimestamp;

    public DBAdSelection(
            long adSelectionId,
            @Nullable CustomAudienceSignals customAudienceSignals,
            @NonNull String contextualSignals,
            @Nullable Uri biddingLogicUrl,
            @NonNull Uri winningAdRenderUrl,
            double winningAdBid,
            @NonNull Instant creationTimestamp) {
        this.mAdSelectionId = adSelectionId;
        this.mCustomAudienceSignals = customAudienceSignals;
        this.mContextualSignals = contextualSignals;
        this.mBiddingLogicUrl = biddingLogicUrl;
        this.mWinningAdRenderUrl = winningAdRenderUrl;
        this.mWinningAdBid = winningAdBid;
        this.mCreationTimestamp = creationTimestamp;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o instanceof DBAdSelection) {
            DBAdSelection adSelection = (DBAdSelection) o;

            return mAdSelectionId == adSelection.mAdSelectionId
                    && Objects.equals(mCustomAudienceSignals, adSelection.mCustomAudienceSignals)
                    && mContextualSignals.equals(adSelection.mContextualSignals)
                    && Objects.equals(mBiddingLogicUrl, adSelection.mBiddingLogicUrl)
                    && Objects.equals(mWinningAdRenderUrl, adSelection.mWinningAdRenderUrl)
                    && mWinningAdBid == adSelection.mWinningAdBid
                    && Objects.equals(mCreationTimestamp, adSelection.mCreationTimestamp);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mAdSelectionId,
                mCustomAudienceSignals,
                mContextualSignals,
                mBiddingLogicUrl,
                mWinningAdRenderUrl,
                mWinningAdBid,
                mCreationTimestamp);
    }

    /**
     * @return the unique ad selection identifier for this ad selection.
     */
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    /**
     * @return the custom audience signals used to select this winning ad if remarketing ads, o.w.
     * return null.
     */
    @Nullable
    public CustomAudienceSignals getCustomAudienceSignals() {
        return mCustomAudienceSignals;
    }

    /**
     * @return the contextual signals i.e. application name used in this ad selection.
     */
    @NonNull
    public String getContextualSignals() {
        return mContextualSignals;
    }

    /**
     * @return the biddingLogicUrl that is used to fetch the generateBid() and the reportResults().
     */
    @Nullable
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * @return the rendering URL of the winning ad in this ad selection.
     */
    @NonNull
    public Uri getWinningAdRenderUrl() {
        return mWinningAdRenderUrl;
    }

    /**
     * @return the bid generated for the winning ad in this ad selection.
     */
    public double getWinningAdBid() {
        return mWinningAdBid;
    }

    /**
     * @return the creation time of this ad selection in the local storage.
     */
    @NonNull
    public Instant getCreationTimestamp() {
        return mCreationTimestamp;
    }

    /** Builder for {@link DBAdSelection} object. */
    public static final class Builder {
        private long mAdSelectionId;
        private CustomAudienceSignals mCustomAudienceSignals;
        private String mContextualSignals;
        private Uri mBiddingLogicUrl;
        private Uri mWinningAdRenderUrl;
        private double mWinningAdBid;
        private Instant mCreationTimestamp;

        public Builder() {
        }

        /** Sets the ad selection id. */
        @NonNull
        public DBAdSelection.Builder setAdSelectionId(long adSelectionId) {
            Preconditions.checkArgument(adSelectionId != 0, "Ad selection Id should not be zero.");
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the custom audience signals. */
        @NonNull
        public DBAdSelection.Builder setCustomAudienceSignals(
                @Nullable CustomAudienceSignals customAudienceSignals) {
            this.mCustomAudienceSignals = customAudienceSignals;
            return this;
        }

        /** Sets the contextual signals with this ad selection. */
        @NonNull
        public DBAdSelection.Builder setContextualSignals(@NonNull String contextualSignals) {
            Objects.requireNonNull(contextualSignals);
            this.mContextualSignals = contextualSignals;
            return this;
        }

        /**
         * Sets the buyer-provided biddingLogicUrl that is used to fetch the generateBid() and
         * reportResults() javascript.
         */
        @NonNull
        public DBAdSelection.Builder setBiddingLogicUrl(@Nullable Uri biddingLogicUrl) {
            this.mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /** Sets the winning ad's rendering URL for this AdSelection. */
        @NonNull
        public DBAdSelection.Builder setWinningAdRenderUrl(@NonNull Uri mWinningAdRenderUrl) {
            Objects.requireNonNull(mWinningAdRenderUrl);
            this.mWinningAdRenderUrl = mWinningAdRenderUrl;
            return this;
        }

        /** Sets the winning ad's bid for this AdSelection. */
        @NonNull
        public DBAdSelection.Builder setWinningAdBid(double winningAdBid) {
            Preconditions.checkArgument(
                    winningAdBid > 0, "A winning ad should not have non-positive bid.");
            this.mWinningAdBid = winningAdBid;
            return this;
        }

        /** Sets the creation time of this ad selection in the table. */
        @NonNull
        public DBAdSelection.Builder setCreationTimestamp(@NonNull Instant creationTimestamp) {
            Objects.requireNonNull(creationTimestamp);
            this.mCreationTimestamp = creationTimestamp;
            return this;
        }

        /**
         * Builds an {@link DBAdSelection} instance.
         *
         * @throws NullPointerException     if any non-nulll params are null.
         * @throws IllegalArgumentException if adSelectionId is zero or bid is non-positive.
         */
        @NonNull
        public DBAdSelection build() {
            Preconditions.checkArgument(mAdSelectionId != 0, "Ad selection Id should not be zero.");
            Preconditions.checkArgument(
                    mWinningAdBid > 0, "A winning ad should not have non-positive bid.");
            Objects.requireNonNull(mContextualSignals);
            Objects.requireNonNull(mWinningAdRenderUrl);
            Objects.requireNonNull(mCreationTimestamp);

            return new DBAdSelection(
                    mAdSelectionId,
                    mCustomAudienceSignals,
                    mContextualSignals,
                    mBiddingLogicUrl,
                    mWinningAdRenderUrl,
                    mWinningAdBid,
                    mCreationTimestamp);
        }
    }
}
