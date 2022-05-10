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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

/** This POJO represents the AdSelectionOverride data in the ad_selection_overrides table entity. */
@AutoValue
@CopyAnnotations
@Entity(tableName = "ad_selection_overrides")
public abstract class DBAdSelectionOverride {

    /**
     * @return AdSelectionConfigId, the primary key of the ad_selection_overrides table
     */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_config_id")
    @PrimaryKey
    @NonNull
    public abstract String getAdSelectionConfigId();

    /**
     * @return App package name
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
     * @return DBAdSelectionOverride built with those params
     */
    public static DBAdSelectionOverride create(
            String adSelectionConfigId, String appPackageName, String decisionLogicJS) {
        return builder()
                .setAdSelectionConfigId(adSelectionConfigId)
                .setAppPackageName(appPackageName)
                .setDecisionLogicJS(decisionLogicJS)
                .build();
    }

    /**
     * @return generic builder
     */
    static DBAdSelectionOverride.Builder builder() {
        return new AutoValue_DBAdSelectionOverride.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract DBAdSelectionOverride.Builder setAdSelectionConfigId(String adSelectionConfigId);

        abstract DBAdSelectionOverride.Builder setAppPackageName(String appPackageName);

        abstract DBAdSelectionOverride.Builder setDecisionLogicJS(String decisionLogicJS);

        abstract DBAdSelectionOverride build();
    }
}
