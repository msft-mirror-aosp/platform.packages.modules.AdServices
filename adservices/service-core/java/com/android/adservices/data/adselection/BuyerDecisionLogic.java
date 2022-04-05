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
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * This POJO represents the BuyerDecisionLogic in the buyer_decision_logic table.
 *
 * @hide
 */
@Entity(tableName = "buyer_decision_logic")
public final class BuyerDecisionLogic {
    @ColumnInfo(name = "bidding_logic_url")
    @PrimaryKey
    @NonNull
    private final Uri mBiddingLogicUrl;

    @ColumnInfo(name = "buyer_decision_logic_js")
    @NonNull
    private final String mBuyerDecisionLogicJs;
    /**
     * @param biddingLogicUrl An {@link Uri} object defining the URL to fetch the buyer-provided
     *     bidding and reporting javascript.
     * @param buyerDecisionLogicJs A {@link String} object contains both the generateBid() and
     *     reportResult() javascript fetched from the biddingLogicUrl.
     */
    public BuyerDecisionLogic(@NonNull Uri biddingLogicUrl, @NonNull String buyerDecisionLogicJs) {
        Objects.requireNonNull(biddingLogicUrl);
        Objects.requireNonNull(buyerDecisionLogicJs);

        mBiddingLogicUrl = biddingLogicUrl;
        mBuyerDecisionLogicJs = buyerDecisionLogicJs;
    }

    /**
     * @return the bidding logic url.
     */
    @NonNull
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * @return the string contains the buyer-side provided generateBit() and reportResult()
     *     javascript.
     */
    @NonNull
    public String getBuyerDecisionLogicJs() {
        return mBuyerDecisionLogicJs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyerDecisionLogic)) return false;
        BuyerDecisionLogic buyerDecisionLogic = (BuyerDecisionLogic) o;
        return mBiddingLogicUrl.equals(buyerDecisionLogic.mBiddingLogicUrl)
                && mBuyerDecisionLogicJs.equals(buyerDecisionLogic.mBuyerDecisionLogicJs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBiddingLogicUrl, mBuyerDecisionLogicJs);
    }
}
