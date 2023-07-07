/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.cobalt.UnencryptedObservationBatch;

/**
 * Stores observations which have been generated, but not sent.
 *
 * <p>Observations are automatically assigned a montonically increasing id.
 */
@AutoValue
@CopyAnnotations
@Entity(tableName = "ObservationStore")
abstract class ObservationStoreEntity {

    /** The id automatically assigned to the observation batch. */
    @CopyAnnotations
    @ColumnInfo(name = "observation_store_id")
    @PrimaryKey(autoGenerate = true)
    @NonNull
    abstract int observationStoreId();

    /** The stored observation batch. */
    @CopyAnnotations
    @ColumnInfo(name = "unencrypted_observation_batch")
    @NonNull
    abstract UnencryptedObservationBatch unencryptedObservationBatch();

    /**
     * Creates an {@link ObservationStoreEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static ObservationStoreEntity create(
            int observationStoreId, UnencryptedObservationBatch unencryptedObservationBatch) {
        return new AutoValue_ObservationStoreEntity(
                observationStoreId, unencryptedObservationBatch);
    }
}
