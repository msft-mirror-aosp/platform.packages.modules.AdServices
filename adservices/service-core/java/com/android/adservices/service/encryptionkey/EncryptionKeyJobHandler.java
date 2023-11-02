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

package com.android.adservices.service.encryptionkey;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.List;
import java.util.Optional;

/** Class for handling encryption key fetch and update. */
public class EncryptionKeyJobHandler {

    private final EncryptionKeyDao mEncryptionKeyDao;
    private final EnrollmentDao mEnrollmentDao;
    private final EncryptionKeyFetcher mEncryptionKeyFetcher;

    EncryptionKeyJobHandler(
            EncryptionKeyDao encryptionKeyDao,
            EnrollmentDao enrollmentDao,
            EncryptionKeyFetcher encryptionKeyFetcher) {
        mEncryptionKeyDao = encryptionKeyDao;
        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyFetcher = encryptionKeyFetcher;
    }

    /** Fetch encryption keys or update expired encryption keys. */
    public void fetchAndUpdateEncryptionKeys() {
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        if (encryptionKeyList.size() == 0) {
            // If no encryption key in table, first time fetch encryption keys for all enrollment
            // data in db.
            List<EnrollmentData> enrollmentDataList = mEnrollmentDao.getAllEnrollmentData();
            for (EnrollmentData enrollmentData : enrollmentDataList) {
                if (Thread.currentThread().isInterrupted()) {
                    LoggerFactory.getLogger()
                            .d(
                                    "EncryptionKeyJobHandler"
                                            + " fetchAndUpdateEncryptionKeys thread interrupted,"
                                            + " exiting early.");
                    return;
                }
                Optional<List<EncryptionKey>> newEncryptionKeys =
                        mEncryptionKeyFetcher.fetchEncryptionKeys(
                                /* encryptionKey */ null,
                                /* enrollmentData */ enrollmentData,
                                /* isFirstTimeFetch */ true);
                if (newEncryptionKeys.isPresent()) {
                    List<EncryptionKey> newEncryptionKeyList = newEncryptionKeys.get();
                    mEncryptionKeyDao.insert(newEncryptionKeyList);
                }
            }
        } else {
            for (EncryptionKey encryptionKey : encryptionKeyList) {
                if (Thread.currentThread().isInterrupted()) {
                    LoggerFactory.getLogger()
                            .d(
                                    "EncryptionKeyJobHandler"
                                            + " fetchAndUpdateEncryptionKeys thread interrupted,"
                                            + " exiting early.");
                    return;
                }
                EnrollmentData enrollmentData =
                        mEnrollmentDao.getEnrollmentData(encryptionKey.getEnrollmentId());
                // When the key doesn't have a corresponding enrollment to it, delete the key, don't
                // need to check expiration time and re-fetch.
                if (enrollmentData == null) {
                    mEncryptionKeyDao.delete(encryptionKey.getId());
                    continue;
                }

                // Re-fetch keys with "if-modified-since" header to check if we need to update keys.
                Optional<List<EncryptionKey>> newEncryptionKeys =
                        mEncryptionKeyFetcher.fetchEncryptionKeys(
                                /* encryptionKey */ encryptionKey,
                                /* enrollmentData */ enrollmentData,
                                /* isFirstTimeFetch */ false);
                if (newEncryptionKeys.isEmpty()) {
                    continue;
                }
                // Overwrite current key with same key type keys in the Http response.
                mEncryptionKeyDao.delete(encryptionKey.getId());
                for (EncryptionKey newKey : newEncryptionKeys.get()) {
                    if (newKey.getKeyType().equals(encryptionKey.getKeyType())) {
                        mEncryptionKeyDao.insert(newKey);
                    }
                }
            }
        }
    }
}
