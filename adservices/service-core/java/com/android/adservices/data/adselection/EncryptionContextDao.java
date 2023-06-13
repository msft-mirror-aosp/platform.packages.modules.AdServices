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

package com.android.adservices.data.adselection;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/** Dao to manage access to entities in Encryption Context table. */
@Dao
public abstract class EncryptionContextDao {

    /** Returns the EncryptionContext of given ad selection id if it exists. */
    @Query(
            ""
                    + "SELECT * FROM encryption_context "
                    + "WHERE context_id = :contextId AND encryption_key_type = :encryptionKeyType")
    public abstract DBEncryptionContext getEncryptionContext(
            long contextId, @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType)
            throws Exception;

    /** Inserts the given EncryptionContext in table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertEncryptionContext(DBEncryptionContext context);
}
