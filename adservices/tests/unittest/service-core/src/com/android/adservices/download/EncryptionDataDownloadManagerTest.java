/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.download;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.download.EncryptionDataDownloadManager.DownloadStatus.FAILURE;
import static com.android.adservices.download.EncryptionDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE;
import static com.android.adservices.download.EncryptionDataDownloadManager.DownloadStatus.SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_FAILED_MDD_FILEGROUP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_MDD_NO_FILE_AVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.shared.util.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Tests for {@link EncryptionDataDownloadManager}. */
@SpyStatic(FlagsFactory.class)
@MockStatic(MobileDataDownloadFactory.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
public final class EncryptionDataDownloadManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_ENCRYPTION_DATA_FILE_DIR = "encryption_keys";
    private static final String DAY_0_JSON_KEY_FILE_NAME = "DAY_0.json";
    private static final String DAY_1_JSON_KEY_FILE_NAME = "DAY_1.json";

    private EncryptionDataDownloadManager mEncryptionDataDownloadManager;
    private SharedDbHelper mDbHelper;
    private EncryptionKeyDao mEncryptionKeyDao;

    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock private ClientConfigProto.ClientFileGroup mMockFileGroup;
    @Mock private ClientConfigProto.ClientFile mMockFile;

    @Mock private MobileDataDownload mMockMdd;
    @Mock private Clock mMockClock;

    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getSharedDbHelperForTest();
        mEncryptionKeyDao = new EncryptionKeyDao(mDbHelper);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
    }

    @After
    public void cleanup() {
        for (String table : EncryptionKeyTables.ENCRYPTION_KEY_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }

    @Test
    public void testGetInstance() {
        mocker.mockGetFlags(mMockFlags);
        EncryptionDataDownloadManager firstInstance = EncryptionDataDownloadManager.getInstance();
        EncryptionDataDownloadManager secondInstance = EncryptionDataDownloadManager.getInstance();

        expect.that(firstInstance).isNotNull();
        expect.that(secondInstance).isNotNull();
        expect.that(firstInstance).isSameInstanceAs(secondInstance);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        // Returns 3 keys expiring on 1. April 24, 2023 2. April 25, 2023 3. April 26, 2023
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        mContext.getAssets()
                                .open(
                                        TEST_ENCRYPTION_DATA_FILE_DIR
                                                + "/"
                                                + DAY_0_JSON_KEY_FILE_NAME));
        // All keys have greater expiration time than this timestamp. (Sep 2, 1996)
        doReturn(841622400000L).when(mMockClock).currentTimeMillis();
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);
        when(mMockFile.getFileUri()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // Verify there are 3 valid unexpired key in the database.
        List<EncryptionKey> databaseKeys = mEncryptionKeyDao.getAllEncryptionKeys();
        expect.that(databaseKeys).hasSize(3);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess_keysUpdated() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        // Returns 3 keys expiring on 1. April 24, 2023 2. April 25, 2023 3. April 26, 2023
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        mContext.getAssets()
                                .open(
                                        TEST_ENCRYPTION_DATA_FILE_DIR
                                                + "/"
                                                + DAY_0_JSON_KEY_FILE_NAME),
                        mContext.getAssets()
                                .open(
                                        TEST_ENCRYPTION_DATA_FILE_DIR
                                                + "/"
                                                + DAY_1_JSON_KEY_FILE_NAME));
        // All keys have greater expiration time than this timestamp. (Sep 2, 1996)
        doReturn(841622400000L).when(mMockClock).currentTimeMillis();
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn(DAY_0_JSON_KEY_FILE_NAME, DAY_1_JSON_KEY_FILE_NAME);
        when(mMockFile.getFileUri()).thenReturn(DAY_0_JSON_KEY_FILE_NAME, DAY_1_JSON_KEY_FILE_NAME);

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        // Run for DAY 0.
        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // Verify there are 3 valid unexpired key in the database.
        List<EncryptionKey> databaseKeys = mEncryptionKeyDao.getAllEncryptionKeys();
        expect.that(databaseKeys).hasSize(3);

        EncryptionKey encryptionKey =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        "TEST0", 12345);
        assertWithMessage("Day 1 encryption key").that(encryptionKey).isNotNull();
        expect.that(encryptionKey.getExpiration()).isEqualTo(1682343722000L);

        // Run for Day 1.
        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // Verify same key has updated expiration now.
        encryptionKey =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        "TEST0", 12345);
        assertWithMessage("Day 1 encryption key").that(encryptionKey).isNotNull();
        expect.that(
                        mEncryptionKeyDao
                                .getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId("TEST0", 12345)
                                .getExpiration())
                .isEqualTo(1712018857000L);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess_deleteExpiredKeys() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        // Returns 3 keys expiring on 1. April 24, 2023 2. April 25, 2023 3. April 26, 2023
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        mContext.getAssets()
                                .open(
                                        TEST_ENCRYPTION_DATA_FILE_DIR
                                                + "/"
                                                + DAY_0_JSON_KEY_FILE_NAME));
        // 2 keys have expired at April 25, 2023, at 10 PM.
        doReturn(1682460000000L).when(mMockClock).currentTimeMillis();
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);
        when(mMockFile.getFileUri()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // Verify there is only 1 valid unexpired key in the database.
        List<EncryptionKey> databaseKeys = mEncryptionKeyDao.getAllEncryptionKeys();
        expect.that(databaseKeys).hasSize(1);
        expect.that(databaseKeys.get(0).getKeyCommitmentId()).isEqualTo(98765);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_MDD_NO_FILE_AVAILABLE)
    public void testReadFileAndInsertIntoDatabaseFailure_missingFileGroup() throws Exception {
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(NO_FILE_AVAILABLE);
        expect.that(mEncryptionKeyDao.getAllEncryptionKeys()).isEmpty();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_MDD_NO_FILE_AVAILABLE)
    public void testReadFileAndInsertIntoDatabaseFailure_emptyFileList() throws Exception {
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(/* Empty list */ List.of());

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(NO_FILE_AVAILABLE);
        expect.that(mEncryptionKeyDao.getAllEncryptionKeys()).isEmpty();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_FAILED_MDD_FILEGROUP)
    public void testReadFileAndInsertIntoDatabaseFailure_fileStorageIOException() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);
        when(mMockFile.getFileUri()).thenReturn(DAY_0_JSON_KEY_FILE_NAME);
        when(mMockFileStorage.open(any(), any())).thenThrow(IOException.class);

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(FAILURE);
        expect.that(mEncryptionKeyDao.getAllEncryptionKeys()).isEmpty();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_MDD_NO_FILE_AVAILABLE)
    public void testReadFileAndInsertIntoDatabaseFailure_fileGroupFutureInterruptedException()
            throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        when(mMockMdd.getFileGroup(any()))
                .thenReturn(Futures.immediateFailedFuture(new InterruptedException()));

        mEncryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, mEncryptionKeyDao, mMockClock);

        expect.that(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(NO_FILE_AVAILABLE);
        expect.that(mEncryptionKeyDao.getAllEncryptionKeys()).isEmpty();
    }
}
