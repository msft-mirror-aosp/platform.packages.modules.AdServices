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

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import com.google.auto.value.AutoValue;

/** Represents data specific to a component ad that is necessary for ad selection and rendering. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = DBComponentAdData.TABLE_NAME,
        foreignKeys =
                @ForeignKey(
                        entity = DBCustomAudience.class,
                        parentColumns = {"owner", "buyer", "name"},
                        childColumns = {"owner", "buyer", "name"},
                        onDelete = ForeignKey.CASCADE),
        primaryKeys = {"owner", "buyer", "name", "renderUri"},
        // Since since we are using {@code AutoValue}, we need to set this parameter to true so that
        // the generated
        // class inherits these indices
        inheritSuperIndices = true)
public abstract class DBComponentAdData {
    public static final String TABLE_NAME = "component_ad_data";

    /**
     * @return the owner of the custom audience that this component ad belongs to.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "owner", index = true)
    @NonNull
    public abstract String getOwner();

    /**
     * @return the buyer of the custom audience that this component ad belongs to.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /**
     * @return the name of the custom audience that this component ad belongs to.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "name", index = true)
    @NonNull
    public abstract String getName();

    /**
     * @return the render uri associated with this component ad.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "renderUri")
    @NonNull
    public abstract Uri getRenderUri();

    /**
     * @return the render id associated with this component ad.
     */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "renderId")
    @NonNull
    public abstract String getRenderId();

    /**
     * Creates a {@link DBComponentAdData} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBComponentAdData create(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull Uri renderUri,
            @NonNull String renderId) {
        return builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setName(name)
                .setRenderUri(renderUri)
                .setRenderId(renderId)
                .build();
    }

    /** Returns an AutoValue builder for a {@link DBComponentAdData} entity. */
    @NonNull
    public static DBComponentAdData.Builder builder() {
        return new AutoValue_DBComponentAdData.Builder();
    }

    /** Builder class for a {@link DBCustomAudienceQuarantine}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the owner. */
        @NonNull
        public abstract Builder setOwner(@NonNull String value);

        /** Sets the buyer. */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /** Sets the name. */
        @NonNull
        public abstract Builder setName(@NonNull String value);

        /** Sets the render uri. */
        @NonNull
        public abstract Builder setRenderUri(@NonNull Uri value);

        /** Sets the render id. */
        @NonNull
        public abstract Builder setRenderId(@NonNull String value);

        /** Builds the {@link DBComponentAdData}. */
        @NonNull
        public abstract DBComponentAdData build();
    }
}
