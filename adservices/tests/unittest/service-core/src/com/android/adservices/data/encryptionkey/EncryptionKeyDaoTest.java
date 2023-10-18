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

import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.encryptionkey.EncryptionKey;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EncryptionKeyDaoTest {

    private SharedDbHelper mDbHelper;
    private EncryptionKeyDao mEncryptionKeyDao;

    private static final EncryptionKey ENCRYPTION_KEY1 =
            new EncryptionKey.Builder()
                    .setId("1")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("AVZBTFVF")
                    .setExpiration(100000L)
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY2 =
            new EncryptionKey.Builder()
                    .setId("2")
                    .setKeyType(EncryptionKey.KeyType.SIGNING)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.WebPKI)
                    .setKeyCommitmentId(12)
                    .setBody("BVZBTFVF")
                    .setExpiration(100000L)
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY3 =
            new EncryptionKey.Builder()
                    .setId("3")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("101")
                    .setReportingOrigin(Uri.parse("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(13)
                    .setBody("CVZBTFVF")
                    .setExpiration(100000L)
                    .build();

    /** Unit test set up. */
    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getSharedDbHelperForTest();
        mEncryptionKeyDao = new EncryptionKeyDao(mDbHelper);
    }

    /** Unit test for EncryptionKeyDao insert() method. */
    @Test
    public void testInsert() {
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
        }
    }

    /** Unit test for EncryptionKeyDao getAllEncryptionKeys() method. */
    @Test
    public void testGetAllEncryptionKeys() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY3);
        List<EncryptionKey> encryptionKeyList = mEncryptionKeyDao.getAllEncryptionKeys();
        assertEquals(3, encryptionKeyList.size());
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
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);
        EncryptionKey encryptionKey =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentId(
                        "100", EncryptionKey.KeyType.ENCRYPTION);

        assertEquals("1", encryptionKey.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey.getProtocolType());
        assertEquals(11, encryptionKey.getKeyCommitmentId());
        assertEquals("AVZBTFVF", encryptionKey.getBody());
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromKeyCommitmentId() method. */
    @Test
    public void testGetEncryptionKeyFromKeyCommitmentId() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);
        EncryptionKey encryptionKey = mEncryptionKeyDao.getEncryptionKeyFromKeyCommitmentId(12);

        assertEquals("2", encryptionKey.getId());
        assertEquals(EncryptionKey.ProtocolType.WebPKI, encryptionKey.getProtocolType());
        assertEquals(12, encryptionKey.getKeyCommitmentId());
        assertEquals("BVZBTFVF", encryptionKey.getBody());
    }

    /** Unit test for EncryptionKeyDao getEncryptionKeyFromReportingOrigin() method. */
    @Test
    public void testGetEncryptionKeyFromReportingOrigin() {
        mEncryptionKeyDao.insert(ENCRYPTION_KEY1);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY2);
        mEncryptionKeyDao.insert(ENCRYPTION_KEY3);
        EncryptionKey encryptionKey1 =
                mEncryptionKeyDao.getEncryptionKeyFromReportingOrigin(
                        Uri.parse("https://test1.com"), EncryptionKey.KeyType.ENCRYPTION);

        assertEquals("1", encryptionKey1.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey1.getProtocolType());
        assertEquals(11, encryptionKey1.getKeyCommitmentId());
        assertEquals("AVZBTFVF", encryptionKey1.getBody());

        EncryptionKey encryptionKey2 =
                mEncryptionKeyDao.getEncryptionKeyFromReportingOrigin(
                        Uri.parse("https://test2.com"), EncryptionKey.KeyType.ENCRYPTION);

        assertEquals("3", encryptionKey2.getId());
        assertEquals(EncryptionKey.ProtocolType.HPKE, encryptionKey2.getProtocolType());
        assertEquals(13, encryptionKey2.getKeyCommitmentId());
        assertEquals("CVZBTFVF", encryptionKey2.getBody());
    }

    /** Unit test cleanup. */
    @After
    public void cleanup() {
        clearAllTables();
    }

    private void clearAllTables() {
        for (String table : EncryptionKeyTables.ENCRYPTION_KEY_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }
}