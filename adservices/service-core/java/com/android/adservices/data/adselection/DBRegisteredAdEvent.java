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

import android.annotation.IntDef;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** This POJO represents the DBRegisteredAdEvent data in the registered_events table entity. */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "registered_ad_events",
        primaryKeys = {"ad_selection_id", "event_type", "destination"})
public abstract class DBRegisteredAdEvent {
    public static final int DESTINATION_SELLER = 0;
    public static final int DESTINATION_BUYER = 1;
    // Will move this @IntDef to a public request object when it is ready
    @IntDef(
            prefix = {"DESTINATION_"},
            value = {DESTINATION_SELLER, DESTINATION_BUYER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Destination {}

    /**
     * @return adSelectionId, the unique identifier for the ad selection process associated with
     *     this registered event
     */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** @return the type of event being registered (e.g., click, view, etc.) */
    @CopyAnnotations
    @ColumnInfo(name = "event_type")
    @NonNull
    public abstract String getEventType();

    /** @return the destination of this registered event during reporting (buyer or seller, etc.) */
    @CopyAnnotations
    @ColumnInfo(name = "destination")
    @Destination
    public abstract int getDestination();

    /** @return Uri to be used during event reporting */
    @CopyAnnotations
    @ColumnInfo(name = "event_uri")
    @NonNull
    public abstract Uri getEventUri();

    /** @return DBRegisteredAdEvent built with those params */
    @NonNull
    public static DBRegisteredAdEvent create(
            long adSelectionId, String eventType, @Destination int destination, Uri eventUri) {
        return builder()
                .setAdSelectionId(adSelectionId)
                .setEventType(eventType)
                .setDestination(destination)
                .setEventUri(eventUri)
                .build();
    }

    /** @return generic builder */
    @NonNull
    public static DBRegisteredAdEvent.Builder builder() {
        return new AutoValue_DBRegisteredAdEvent.Builder();
    }

    /** AutoValue Builder */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the adSelectionId for the {@link DBRegisteredAdEvent} entry. */
        @NonNull
        public abstract DBRegisteredAdEvent.Builder setAdSelectionId(long adSelectionId);

        /** Sets the eventType for the {@link DBRegisteredAdEvent} entry. */
        @NonNull
        public abstract DBRegisteredAdEvent.Builder setEventType(@NonNull String eventType);

        /** Sets the destination for the {@link DBRegisteredAdEvent} entry. */
        @NonNull
        public abstract DBRegisteredAdEvent.Builder setDestination(@Destination int destination);

        /** Sets the eventUri for the {@link DBRegisteredAdEvent} entry. */
        @NonNull
        public abstract DBRegisteredAdEvent.Builder setEventUri(@NonNull Uri eventUri);

        /**
         * @return an instance of {@link DBRegisteredAdEvent} built with the information in this
         *     builder.
         */
        @NonNull
        public abstract DBRegisteredAdEvent build();
    }
}
