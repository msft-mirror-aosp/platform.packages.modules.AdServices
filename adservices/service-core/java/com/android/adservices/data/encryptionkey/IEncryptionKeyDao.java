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

package com.android.adservices.data.encryptionkey;

import android.net.Uri;

import com.android.adservices.service.encryptionkey.EncryptionKey;

import java.util.List;

/** Interface for encryption key related data access operations. */
public interface IEncryptionKeyDao {

    /**
     * Returns the {@link EncryptionKey}.
     *
     * @param enrollmentId ID provided to the adtech during the enrollment process.
     * @param keyType the key type of this key, can be either encryption key or signing key.
     * @return the EncryptionKey; Null in case of SQL failure
     */
    EncryptionKey getEncryptionKeyFromEnrollmentId(
            String enrollmentId, EncryptionKey.KeyType keyType);

    /**
     * Returns the {@link EncryptionKey}.
     *
     * @param keyCommitmentId key commitment id provided by adtech.
     * @return the EncryptionKey; Null in case of SQL failure
     */
    EncryptionKey getEncryptionKeyFromKeyCommitmentId(int keyCommitmentId);

    /**
     * Returns the {@link EncryptionKey}.
     *
     * @param reportingOrigin provided as triggerRegistrationUrl during trigger attestation.
     * @return the EncryptionKey; Null in case of SQL failure
     */
    EncryptionKey getEncryptionKeyFromReportingOrigin(
            Uri reportingOrigin, EncryptionKey.KeyType keyType);

    /**
     * Returns all the {@link EncryptionKey} in the table.
     *
     * @return all the encryption keys in the table.
     */
    List<EncryptionKey> getAllEncryptionKeys();

    /**
     * Inserts {@link EncryptionKey} into DB table.
     *
     * @param encryptionKey the EncryptionKey to insert.
     * @return true if the operation was successful, false, otherwise.
     */
    boolean insert(EncryptionKey encryptionKey);

    /**
     * Deletes {@link EncryptionKey} from DB table.
     *
     * @param id id of the EncryptionKey.
     * @return true if the operation was successful, false, otherwise.
     */
    boolean delete(String id);
}
