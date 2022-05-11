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
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/**
 * This POJO represents the CustomAudienceOverride data in the custom_audience_overrides table
 * entity.
 */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "custom_audience_overrides",
        primaryKeys = {"owner", "buyer", "name"})
public abstract class DBCustomAudienceOverride {
    /**
     * @return the owner
     */
    @CopyAnnotations
    @ColumnInfo(name = "owner")
    @NonNull
    public abstract String getOwner();

    /**
     * @return the buyer
     */
    @CopyAnnotations
    @ColumnInfo(name = "buyer")
    @NonNull
    public abstract String getBuyer();

    /**
     * @return name
     */
    @CopyAnnotations
    @ColumnInfo(name = "name")
    @NonNull
    public abstract String getName();

    /**
     * @return App package name, app package name associated with this CustomAudienceId
     */
    @CopyAnnotations
    @ColumnInfo(name = "app_package_name")
    @NonNull
    public abstract String getAppPackageName();

    /**
     * @return The override javascript result
     */
    @CopyAnnotations
    @ColumnInfo(name = "decision_logic")
    @NonNull
    public abstract String getDecisionLogicJS();

    /**
     * @return The override trusted bidding data result
     */
    @CopyAnnotations
    @ColumnInfo(name = "trusted_bidding_data")
    @NonNull
    public abstract String getTrustedBiddingData();

    /**
     * @return DBAdSelectionOverride built with those params
     */
    public static DBCustomAudienceOverride create(
            String owner,
            String buyer,
            String name,
            String appPackageName,
            String decisionLogicJS,
            String trustedBiddingData) {
        return builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setName(name)
                .setAppPackageName(appPackageName)
                .setDecisionLogicJS(decisionLogicJS)
                .setTrustedBiddingData(trustedBiddingData)
                .build();
    }

    /**
     * @return generic builder
     */
    static DBCustomAudienceOverride.Builder builder() {
        return new AutoValue_DBCustomAudienceOverride.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract DBCustomAudienceOverride.Builder setOwner(String owner);

        abstract DBCustomAudienceOverride.Builder setBuyer(String buyer);

        abstract DBCustomAudienceOverride.Builder setName(String name);

        abstract DBCustomAudienceOverride.Builder setAppPackageName(String appPackageName);

        abstract DBCustomAudienceOverride.Builder setDecisionLogicJS(String decisionLogicJs);

        abstract DBCustomAudienceOverride.Builder setTrustedBiddingData(String trustedBiddingData);

        abstract DBCustomAudienceOverride build();
    }
}
