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

import static com.android.adservices.download.EncryptionDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE;
import static com.android.adservices.download.EncryptionDataDownloadManager.DownloadStatus.SUCCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

/** Tests for {@link EncryptionDataDownloadManager}. */
@SpyStatic(FlagsFactory.class)
@MockStatic(ErrorLogUtil.class)
@MockStatic(MobileDataDownloadFactory.class)
@MockStatic(EncryptionKeyDao.class)
@RequiresSdkLevelAtLeastS
public final class EncryptionDataDownloadManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_ENCRYPTION_DATA_FILE_DIR = "encryption_keys";
    private static final String TEST0_JSON_KEY_FILE_NAME = "TEST0.json";
    private EncryptionDataDownloadManager mEncryptionDataDownloadManager;

    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock private ClientConfigProto.ClientFileGroup mMockFileGroup;
    @Mock private ClientConfigProto.ClientFile mMockFile;

    @Mock private MobileDataDownload mMockMdd;
    @Mock private EncryptionKeyDao mMockEncryptionKeyDao;
    @Mock private Flags mMockFlags;

    @Test
    public void testGetInstance() {
        EncryptionDataDownloadManager firstInstance = EncryptionDataDownloadManager.getInstance();
        EncryptionDataDownloadManager secondInstance = EncryptionDataDownloadManager.getInstance();

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isSameInstanceAs(secondInstance);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess() throws Exception {
        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEncryptionKeyDao).when(() -> (EncryptionKeyDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        sContext.getAssets()
                                .open(
                                        TEST_ENCRYPTION_DATA_FILE_DIR
                                                + "/"
                                                + TEST0_JSON_KEY_FILE_NAME));

        mEncryptionDataDownloadManager = new EncryptionDataDownloadManager(sContext, mMockFlags);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn(TEST0_JSON_KEY_FILE_NAME);
        when(mMockFile.getFileUri()).thenReturn(TEST0_JSON_KEY_FILE_NAME);

        ArgumentCaptor<List<EncryptionKey>> encryptionKeyCaptor =
                ArgumentCaptor.forClass(List.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        assertThat(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(SUCCESS);

        verify(mMockEncryptionKeyDao).insert((List<EncryptionKey>) any());
        // There are 3 keys
        assertThat(encryptionKeyCaptor.getValue()).hasSize(3);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseFailure_missingFileGroup() throws Exception {
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));

        mEncryptionDataDownloadManager = new EncryptionDataDownloadManager(sContext, mMockFlags);

        assertThat(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(NO_FILE_AVAILABLE);
        verify(mMockEncryptionKeyDao, never()).insert((List<EncryptionKey>) any());
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseFailure_emptyFileList() throws Exception {
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(/* Empty list */ List.of());

        mEncryptionDataDownloadManager = new EncryptionDataDownloadManager(sContext, mMockFlags);

        assertThat(mEncryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(NO_FILE_AVAILABLE);
        verify(mMockEncryptionKeyDao, never()).insert((List<EncryptionKey>) any());
    }
}
