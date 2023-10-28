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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.encryptionkey.EncryptionKey;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EncryptionKeyDaoTest {

    private SharedDbHelper mDbHelper;
    private EncryptionKeyDao mEncryptionKeyDao;

    public static final EncryptionKey ENCRYPTION_KEY1 =
            new EncryptionKey.Builder()
                    .setId("1")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("AVZBTFVF")
                    .setExpiration(100001L)
                    .setLastFetchTime(100001L)
                    .build();

    private static final EncryptionKey DUPLICATE_ENCRYPTION_KEY1 =
            new EncryptionKey.Builder()
                    .setId("1111")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("AVZBTFVF")
                    .setExpiration(100001L)
                    .setLastFetchTime(100001L)
                    .build();

    public static final EncryptionKey SIGNING_KEY1 =
            new EncryptionKey.Builder()
                    .setId("2")
                    .setKeyType(EncryptionKey.KeyType.SIGNING)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.ECDSA)
                    .setKeyCommitmentId(12)
                    .setBody("BVZBTFVF")
                    .setExpiration(100002L)
                    .setLastFetchTime(100002L)
                    .build();

    private static final EncryptionKey SIGNING_KEY2 =
            new EncryptionKey.Builder()
                    .setId("3")
                    .setKeyType(EncryptionKey.KeyType.SIGNING)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.ECDSA)
                    .setKeyCommitmentId(13)
                    .setBody("CVZBTFVF")
                    .setExpiration(100003L)
                    .setLastFetchTime(100003L)
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY2 =
            new EncryptionKey.Builder()
                    .setId("4")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("101")
                    .setReportingOrigin(Uri.parse("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(14)
                    .setBody("DVZBTFVF")
                    .setExpiration(100004L)
                    .setLastFetchTime(100004L)
                    .build();

    private static final EncryptionKey INVALID_KEY =
            new EncryptionKey.Builder()
                    .setId("5")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("102")
                    .setReportingOrigin(Uri.parse("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(15)
                    .setExpiration(100000L)
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY1_SAME_KEY_COMMITMENT_ID_FOR_DIFFERENT_ADTECH =
            new EncryptionKey.Builder()
                    .setId("6")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("103")
                    .setReportingOrigin(Uri.parse("https://test3.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("FVZBTFVF")
                    .setExpiration(100006L)
                    .build();

    /** Unit test set up. */
    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getSharedDbHelperForTest();
        mEncryptionKeyDao = new EncryptionKeyDao(mDbHelper);
    }

    /** Unit test for EncryptionKeyDao insert() method. */
    @Test
    public void testInsertNewEncryptionKey() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);

        try (Cursor cursor =
                mDbHelper
                        .getReadableDatabase()
                        .query(
                                EncryptionKeyTables.EncryptionKeyContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            EncryptionKey encryptionKey =
                    SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor);
            Assert.assertNotNull(encryptionKey);
            assertEquals(encryptionKey.getId(), ENCRYPTION_KEY1.getId());
            assertEquals(encryptionKey.getKeyType(), ENCRYPTION_KEY1.getKeyType());
            assertEquals(encryptionKey.getEnrollmentId(), ENCRYPTION_KEY1.getEnrollmentId());
            assertEquals(encryptionKey.getReportingOrigin(), ENCRYPTION_KEY1.getReportingOrigin());
            assertEquals(
                    encryptionKey.getEncryptionKeyUrl(), ENCRYPTION_KEY1.getEncryptionKeyUrl());
            assertEquals(encryptionKey.getProtocolType(), ENCRYPTION_KEY1.getProtocolType());
            assertEquals(encryptionKey.getKeyCommitmentId(), ENCRYPTION_KEY1.getKeyCommitmentId());
            assertEquals(encryptionKey.getBody(), ENCRYPTION_KEY1.getBody());
            assertEquals(encryptionKey.getExpiration(), ENCRYPTION_KEY1.getExpiration());
            assertEquals(encryptionKey.getLastFetchTime(), ENCRYPTION_KEY1.getLastFetchTime());
        }
    }

    @Test
    public void testInsertExistingEncryptionKey() {
        assertTrue(mEncryptionKeyDao.insert(ENCRYPTION_KEY1));
        assertTrue(mEncryptionKeyDao.insert(DUPLICATE_ENCRYPTION_KEY1));
        assertEquals(1, mEncryptionKeyDao.getAllEncryptionKeys().size());
        EncryptionKey encryptionKey =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        DUPLICATE_ENCRYPTION_KEY1.getEnrollmentId(),
                        DUPLICATE_ENCRYPTION_KEY1.getKeyCommitmentId());
        assertNotNull(encryptionKey);
        assertEquals("1111", encryptionKey.getId());
    }

    @Test
    public void testInsertInvalidEncryptionKey() {
        mEncryptionKeyDao.insert(INVALID_KEY);
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        assertEquals(0, encryptionKeyList.size());
    }

    @Test
    public void testInsertEncryptionKeyList() {
        List<EncryptionKey> encryptionKeyList = Arrays.asList(ENCRYPTION_KEY1, SIGNING_KEY1);
        mEncryptionKeyDao.insert(encryptionKeyList);

        assertEquals(2, mEncryptionKeyDao.getAllEncryptionKeys().size());
    }

    /** Unit test for EncryptionKeyDao getAllEncryptionKeys() method. */
    @Test
    public void testGetAllEncryptionKeys() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY2);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        assertEquals(4, encryptionKeyList.size());
    }

    /** Unit test for EncryptionKeyDao delete() method. */
    @Test
    public void testDelete() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        assertEquals(1, encryptionKeyList.size());

        String id = encryptionKeyList.get(0).getId();
        mEncryptionKeyDao.delete(id);
        List<EncryptionKey> emptyList = mEncryptionKeyDao.getAllEncryptionKeys();
        assertEquals(0, emptyList.size());
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromEnrollmentId() method. */
    @Test
    public void testGetEncryptionKeyFromEnrollmentId() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY2);

        List<EncryptionKey> encryptionKeyList =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentId("100");
        assertNotNull(encryptionKeyList);
        assertEquals(3, encryptionKeyList.size());
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromEnrollmentIdAndKeyType() method. */
    @Test
    public void testGetEncryptionKeyFromEnrollmentIdAndKeyType() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY2);

        List<EncryptionKey> encryptionKeyList =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        "100", EncryptionKey.KeyType.ENCRYPTION);
        assertNotNull(encryptionKeyList);
        assertEquals(1, encryptionKeyList.size());
        EncryptionKey encryptionKey = encryptionKeyList.get(0);
        assertEquals("1", encryptionKey.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey.getProtocolType());
        assertEquals(11, encryptionKey.getKeyCommitmentId());
        assertEquals("AVZBTFVF", encryptionKey.getBody());
        assertEquals(100001L, encryptionKey.getExpiration());
        assertEquals(100001L, encryptionKey.getLastFetchTime());

        List<EncryptionKey> signingKeyList =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        "100", EncryptionKey.KeyType.SIGNING);
        assertNotNull(signingKeyList);
        assertSigningKeyListResult(signingKeyList);
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromKeyCommitmentId() method. */
    @Test
    public void testGetEncryptionKeyFromKeyCommitmentId() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1_SAME_KEY_COMMITMENT_ID_FOR_DIFFERENT_ADTECH);
        EncryptionKey encryptionKey =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        ENCRYPTION_KEY1.getEnrollmentId(), ENCRYPTION_KEY1.getKeyCommitmentId());

        assertNotNull(encryptionKey);
        assertEquals("1", encryptionKey.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey.getProtocolType());
        assertEquals(11, encryptionKey.getKeyCommitmentId());
        assertEquals("AVZBTFVF", encryptionKey.getBody());
        assertEquals(100001L, encryptionKey.getExpiration());
        assertEquals(100001L, encryptionKey.getLastFetchTime());
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromReportingOrigin() method. */
    @Test
    public void testGetEncryptionKeyFromReportingOrigin() {
        mEncryptionKeyDao.insert(SIGNING_KEY1);
        mEncryptionKeyDao.insert(SIGNING_KEY2);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);

        List<EncryptionKey> signingKeyList =
                mEncryptionKeyDao.getEncryptionKeyFromReportingOrigin(
                        Uri.parse("https://test1.com"), EncryptionKey.KeyType.SIGNING);
        assertNotNull(signingKeyList);
        assertSigningKeyListResult(signingKeyList);

        List<EncryptionKey> encryptionKeyList =
                mEncryptionKeyDao.getEncryptionKeyFromReportingOrigin(
                        Uri.parse("https://test2.com"), EncryptionKey.KeyType.ENCRYPTION);
        assertNotNull(encryptionKeyList);
        assertEquals(1, encryptionKeyList.size());
        EncryptionKey encryptionKey = encryptionKeyList.get(0);
        assertEquals("4", encryptionKey.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey.getProtocolType());
        assertEquals(14, encryptionKey.getKeyCommitmentId());
        assertEquals("DVZBTFVF", encryptionKey.getBody());
        assertEquals(100004L, encryptionKey.getExpiration());
        assertEquals(100004L, encryptionKey.getLastFetchTime());
    }

    /** Unit test cleanup. */
    @After
    public void cleanup() {
        clearAllTables();
    }

    private void assertSigningKeyListResult(List<EncryptionKey> signingKeyList) {
        assertEquals(2, signingKeyList.size());
        EncryptionKey signingKey1 = signingKeyList.get(0);
        assertEquals(EncryptionKey.ProtocolType.ECDSA, signingKey1.getProtocolType());
        assertEquals(12, signingKey1.getKeyCommitmentId());
        assertEquals("BVZBTFVF", signingKey1.getBody());
        assertEquals(100002L, signingKey1.getExpiration());
        assertEquals(100002L, signingKey1.getLastFetchTime());

        EncryptionKey signingKey2 = signingKeyList.get(1);
        assertEquals(EncryptionKey.ProtocolType.ECDSA, signingKey2.getProtocolType());
        assertEquals(13, signingKey2.getKeyCommitmentId());
        assertEquals("CVZBTFVF", signingKey2.getBody());
        assertEquals(100003L, signingKey2.getExpiration());
        assertEquals(100003L, signingKey2.getLastFetchTime());
    }

    private void clearAllTables() {
        for (String table : EncryptionKeyTables.ENCRYPTION_KEY_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }
}
