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

package com.android.adservices.download;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.encryptionkey.EncryptionKeyDaoTest;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.encryptionkey.EncryptionKeyFetcher;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

@SpyStatic(FlagsFactory.class)
@MockStatic(MobileDataDownloadFactory.class)
@MockStatic(EnrollmentDao.class)
@MockStatic(EncryptionKeyDao.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
public final class EnrollmentDataDownloadManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_ENROLLMENT_DATA_FILE_PATH =
            "enrollment/adtech_enrollment_data.csv";
    private static final String TEST_ENROLLMENT_DATA_PROTO_FILE_PATH =
            "enrollment/rb_prod_enrollment.binarypb";
    private EnrollmentDataDownloadManager mEnrollmentDataDownloadManager;

    @Mock private SynchronousFileStorage mMockFileStorage;

    @Mock private EnrollmentDao mMockEnrollmentDao;

    @Mock private MobileDataDownload mMockMdd;
    @Mock private AdServicesLogger mLogger;
    @Mock private EnrollmentUtil mEnrollmentUtil;
    @Mock private EncryptionKeyDao mMockEncryptionKeyDao;
    @Mock private EncryptionKeyFetcher mEncryptionKeyFetcher;

    @After
    public void cleanup() {
        mContext.getSharedPreferences("enrollment_data_read_status", 0).edit().clear().commit();
        mContext.getSharedPreferences("adservices_enrollment", 0).edit().clear().commit();
    }

    @Test
    public void testGetInstance() {
        mocker.mockGetFlags(mMockFlags);
        EnrollmentDataDownloadManager firstInstance = EnrollmentDataDownloadManager.getInstance();
        EnrollmentDataDownloadManager secondInstance = EnrollmentDataDownloadManager.getInstance();

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess()
            throws IOException, ExecutionException, InterruptedException {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockEncryptionKeyDao).when(EncryptionKeyDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNetworkConnectTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
        when(mMockFlags.getEncryptionKeyNetworkReadTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);

        ArgumentCaptor<EnrollmentData> enrollmentDataCaptor =
                ArgumentCaptor.forClass(EnrollmentData.class);
        doReturn(true).when(mMockEnrollmentDao).insert(enrollmentDataCaptor.capture());

        ArgumentCaptor<String> enrollmentIdCaptor = ArgumentCaptor.forClass(String.class);
        List<EncryptionKey> existingEncryptionKeys = new ArrayList<>();
        doReturn(existingEncryptionKeys)
                .when(mMockEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentId(enrollmentIdCaptor.capture());

        List<EncryptionKey> encryptionKeyList =
                Arrays.asList(
                        EncryptionKeyDaoTest.ENCRYPTION_KEY1, EncryptionKeyDaoTest.SIGNING_KEY1);
        Optional<List<EncryptionKey>> fetchResult = Optional.of(encryptionKeyList);
        ArgumentCaptor<Boolean> isFirstFetchCaptor = ArgumentCaptor.forClass(Boolean.class);
        doReturn(fetchResult)
                .when(mEncryptionKeyFetcher)
                .fetchEncryptionKeys(
                        any(), enrollmentDataCaptor.capture(), isFirstFetchCaptor.capture());

        ArgumentCaptor<EncryptionKey> encryptionKeyCaptor =
                ArgumentCaptor.forClass(EncryptionKey.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);

        verify(mMockEnrollmentDao, times(5)).insert(any());
        verify(mMockEncryptionKeyDao, times(10)).insert((EncryptionKey) any());
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseFileGroupNull() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, never()).insert(any());
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseEnrollmentDataFileIdMissing() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("wrong_file_id.csv")
                                                        .build())
                                        .build()));

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, never()).insert(any());
        verifyZeroInteractions(mLogger);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE)
    public void testReadFileAndInsertIntoDatabaseExecutionException() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(Futures.immediateFailedFuture(new CancellationException()));

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, never()).insert(any());
        verifyZeroInteractions(mLogger);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING)
    public void testReadFileAndInsertIntoDatabaseParsingFailed() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockFileStorage.open(any(), any())).thenThrow(new IOException());
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);

        ArgumentCaptor<EnrollmentData> captor = ArgumentCaptor.forClass(EnrollmentData.class);

        doReturn(true).when(mMockEnrollmentDao).insert(captor.capture());

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.PARSING_FAILED);

        verify(mMockEnrollmentDao, never()).insert(any());
    }

    @Test
    public void testEnrollmentMddRecordDeletionCallsOverwrite() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockEncryptionKeyDao).when(EncryptionKeyDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(true);
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNetworkConnectTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
        when(mMockFlags.getEncryptionKeyNetworkReadTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);

        doReturn(true).when(mMockEnrollmentDao).overwriteData(any());
        ArgumentCaptor<String> enrollmentIdCaptor = ArgumentCaptor.forClass(String.class);
        List<EncryptionKey> existingEncryptionKeys = new ArrayList<>();
        doReturn(existingEncryptionKeys)
                .when(mMockEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentId(enrollmentIdCaptor.capture());

        List<EncryptionKey> encryptionKeyList =
                Arrays.asList(
                        EncryptionKeyDaoTest.ENCRYPTION_KEY1, EncryptionKeyDaoTest.SIGNING_KEY1);
        Optional<List<EncryptionKey>> fetchResult = Optional.of(encryptionKeyList);
        ArgumentCaptor<EnrollmentData> enrollmentDataCaptor =
                ArgumentCaptor.forClass(EnrollmentData.class);
        ArgumentCaptor<Boolean> isFirstFetchCaptor = ArgumentCaptor.forClass(Boolean.class);
        doReturn(fetchResult)
                .when(mEncryptionKeyFetcher)
                .fetchEncryptionKeys(
                        any(), enrollmentDataCaptor.capture(), isFirstFetchCaptor.capture());

        ArgumentCaptor<EncryptionKey> encryptionKeyCaptor =
                ArgumentCaptor.forClass(EncryptionKey.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);

        verify(mMockEnrollmentDao, times(1)).overwriteData(any());

        verify(mMockEnrollmentDao, never()).insert(any());

        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess_verifyFileGroupDataInSharedPreference()
            throws IOException, ExecutionException, InterruptedException {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockEncryptionKeyDao).when(EncryptionKeyDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .setBuildId(1)
                                        .setStatus(ClientFileGroup.Status.PENDING)
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNetworkConnectTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
        when(mMockFlags.getEncryptionKeyNetworkReadTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);

        ArgumentCaptor<EnrollmentData> enrollmentDataCaptor =
                ArgumentCaptor.forClass(EnrollmentData.class);
        doReturn(true).when(mMockEnrollmentDao).insert(enrollmentDataCaptor.capture());

        ArgumentCaptor<String> enrollmentIdCaptor = ArgumentCaptor.forClass(String.class);
        List<EncryptionKey> existingEncryptionKeys = new ArrayList<>();
        doReturn(existingEncryptionKeys)
                .when(mMockEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentId(enrollmentIdCaptor.capture());

        List<EncryptionKey> encryptionKeyList =
                Arrays.asList(
                        EncryptionKeyDaoTest.ENCRYPTION_KEY1, EncryptionKeyDaoTest.SIGNING_KEY1);
        Optional<List<EncryptionKey>> fetchResult = Optional.of(encryptionKeyList);
        ArgumentCaptor<Boolean> isFirstFetchCaptor = ArgumentCaptor.forClass(Boolean.class);
        doReturn(fetchResult)
                .when(mEncryptionKeyFetcher)
                .fetchEncryptionKeys(
                        any(), enrollmentDataCaptor.capture(), isFirstFetchCaptor.capture());

        ArgumentCaptor<EncryptionKey> encryptionKeyCaptor =
                ArgumentCaptor.forClass(EncryptionKey.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);

        verify(mMockEnrollmentDao, times(5)).insert(any());
        verify(mMockEncryptionKeyDao, times(10)).insert((EncryptionKey) any());
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);

        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();
        assertThat(enrollmentUtil.getBuildId()).isEqualTo(1);
        assertThat(enrollmentUtil.getFileGroupStatus()).isEqualTo(2);
    }

    private void verifyEnrollmentDataDownloadStatus(
            EnrollmentDataDownloadManager.DownloadStatus status)
            throws InterruptedException, ExecutionException {
        assertThat(mEnrollmentDataDownloadManager.readAndInsertEnrollmentDataFromMdd().get())
                .isEqualTo(status);
    }

    @Test
    public void testReadProtoFileAndInsertIntoDatabaseSuccess() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockEncryptionKeyDao).when(EncryptionKeyDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_PROTO_FILE_PATH));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        GetFileGroupRequest getProtoFileGroupRequest =
                GetFileGroupRequest.newBuilder()
                        .setGroupName("adtech_enrollment_proto_data")
                        .build();
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName("adtech_enrollment_data").build();

        when(mMockMdd.getFileGroup(getProtoFileGroupRequest))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("rb_prod_enrollment.binarypb")
                                                        .setFileUri("rb_prod_enrollment.binarypb")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));

        when(mMockMdd.getFileGroup(getFileGroupRequest))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .setBuildId(2)
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNetworkConnectTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
        when(mMockFlags.getEncryptionKeyNetworkReadTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);
        when(mMockFlags.getEnrollmentProtoFileEnabled()).thenReturn(true);

        ArgumentCaptor<EnrollmentData> enrollmentDataCaptor =
                ArgumentCaptor.forClass(EnrollmentData.class);
        doReturn(true).when(mMockEnrollmentDao).insert(enrollmentDataCaptor.capture());

        ArgumentCaptor<String> enrollmentIdCaptor = ArgumentCaptor.forClass(String.class);
        List<EncryptionKey> existingEncryptionKeys = new ArrayList<>();
        doReturn(existingEncryptionKeys)
                .when(mMockEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentId(enrollmentIdCaptor.capture());

        List<EncryptionKey> encryptionKeyList =
                Arrays.asList(
                        EncryptionKeyDaoTest.ENCRYPTION_KEY1, EncryptionKeyDaoTest.SIGNING_KEY1);
        Optional<List<EncryptionKey>> fetchResult = Optional.of(encryptionKeyList);
        ArgumentCaptor<Boolean> isFirstFetchCaptor = ArgumentCaptor.forClass(Boolean.class);
        doReturn(fetchResult)
                .when(mEncryptionKeyFetcher)
                .fetchEncryptionKeys(
                        any(), enrollmentDataCaptor.capture(), isFirstFetchCaptor.capture());

        ArgumentCaptor<EncryptionKey> encryptionKeyCaptor =
                ArgumentCaptor.forClass(EncryptionKey.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);
        verify(mMockEnrollmentDao, times(5)).insert(any());
        verify(mMockEncryptionKeyDao, times(10)).insert((EncryptionKey) any());
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));
        verify(mEnrollmentUtil, never())
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("2"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_FAILED_PARSING)
    public void testReadProtoFileAndInsertIntoDatabaseParsingFailed() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockFileStorage.open(any(), any())).thenThrow(new IOException());
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("rb_prod_enrollment.binarypb")
                                                        .setFileUri("rb_prod_enrollment.binarypb")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEnrollmentProtoFileEnabled()).thenReturn(true);

        ArgumentCaptor<EnrollmentData> captor = ArgumentCaptor.forClass(EnrollmentData.class);

        doReturn(true).when(mMockEnrollmentDao).insert(captor.capture());

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.PARSING_FAILED);

        verifyZeroInteractions(mMockEnrollmentDao);
    }

    @Test
    public void testReadProtoFileAndInsertIntoDatabaseSuccess_noProtoFileFound() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        doReturn(mMockEncryptionKeyDao).when(EncryptionKeyDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("adtech_enrollment_data.csv")
                                                        .setFileUri("adtech_enrollment_data.csv")
                                                        .build())
                                        .setBuildId(1)
                                        .setStatus(ClientFileGroup.Status.PENDING)
                                        .build()));

        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyNetworkConnectTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS);
        when(mMockFlags.getEncryptionKeyNetworkReadTimeoutMs())
                .thenReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS);
        when(mMockFlags.getEnrollmentProtoFileEnabled()).thenReturn(true);

        ArgumentCaptor<EnrollmentData> enrollmentDataCaptor =
                ArgumentCaptor.forClass(EnrollmentData.class);
        doReturn(true).when(mMockEnrollmentDao).insert(enrollmentDataCaptor.capture());

        ArgumentCaptor<String> enrollmentIdCaptor = ArgumentCaptor.forClass(String.class);
        List<EncryptionKey> existingEncryptionKeys = new ArrayList<>();
        doReturn(existingEncryptionKeys)
                .when(mMockEncryptionKeyDao)
                .getEncryptionKeyFromEnrollmentId(enrollmentIdCaptor.capture());

        List<EncryptionKey> encryptionKeyList =
                Arrays.asList(
                        EncryptionKeyDaoTest.ENCRYPTION_KEY1, EncryptionKeyDaoTest.SIGNING_KEY1);
        Optional<List<EncryptionKey>> fetchResult = Optional.of(encryptionKeyList);
        ArgumentCaptor<Boolean> isFirstFetchCaptor = ArgumentCaptor.forClass(Boolean.class);
        doReturn(fetchResult)
                .when(mEncryptionKeyFetcher)
                .fetchEncryptionKeys(
                        any(), enrollmentDataCaptor.capture(), isFirstFetchCaptor.capture());

        ArgumentCaptor<EncryptionKey> encryptionKeyCaptor =
                ArgumentCaptor.forClass(EncryptionKey.class);
        doReturn(true).when(mMockEncryptionKeyDao).insert(encryptionKeyCaptor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);
        verify(mMockEnrollmentDao, times(5)).insert(any());
        verify(mMockEncryptionKeyDao, times(10)).insert((EncryptionKey) any());
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    @Test
    public void testReadProtoFileAndInsertIntoDatabaseFailed_noFilesFound() throws Exception {
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(mContext.getAssets().open(TEST_ENROLLMENT_DATA_PROTO_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        mContext, mMockFlags, mLogger, mEnrollmentUtil, mEncryptionKeyFetcher);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("wrong_file_id.binarypb")
                                                        .setFileUri("wrong_file_id.binarypb")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                ClientFileGroup.newBuilder()
                                        .addFile(
                                                ClientFile.newBuilder()
                                                        .setFileId("wrong_file_id.csv")
                                                        .setFileUri("wrong_file_id.csv")
                                                        .build())
                                        .setBuildId(1)
                                        .build()));

        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);
        when(mMockFlags.getEnrollmentProtoFileEnabled()).thenReturn(true);

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mLogger);
    }
}
