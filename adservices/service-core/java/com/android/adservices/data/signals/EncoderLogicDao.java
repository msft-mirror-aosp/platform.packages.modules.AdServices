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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Dao to persist, access and delete encoding logic for buyers */
@Dao
public interface EncoderLogicDao {

    /**
     * @param logic an entry for encoding logic
     * @return the rowId of the entry persisted
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long persistEncoder(DBEncoderLogic logic);

    /**
     * @param buyer Ad-tech owner for the encoding logic
     * @return an instance of {@link DBEncoderLogic} if present
     */
    @Query("SELECT * FROM encoder_logic WHERE buyer = :buyer")
    DBEncoderLogic getEncoder(AdTechIdentifier buyer);

    /**
     * @param buyer Ad-tech owner for the encoding logic
     * @return true if the encoder for the buyer exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM encoder_logic WHERE buyer = :buyer)")
    boolean doesEncoderExist(AdTechIdentifier buyer);

    /**
     * @return list of all the buyers which have their encoder logic registered
     */
    @Query("SELECT DISTINCT buyer FROM encoder_logic")
    List<AdTechIdentifier> getAllBuyersWithRegisteredEncoders();

    /**
     * @return list of buyers which registered encoders before the expiryTime
     */
    @Query("SELECT DISTINCT buyer FROM encoder_logic WHERE creation_time < :expiryTime")
    List<AdTechIdentifier> getBuyersWithEncodersBeforeTime(@NonNull Instant expiryTime);

    /**
     * @param buyer Ad-tech identifier whose encoding logic we delete
     */
    @Query("DELETE FROM encoder_logic WHERE buyer = :buyer")
    void deleteEncoder(AdTechIdentifier buyer);

    /** Deletes all persisted encoding logic */
    @Query("DELETE FROM encoder_logic")
    void deleteAllEncoders();
}
