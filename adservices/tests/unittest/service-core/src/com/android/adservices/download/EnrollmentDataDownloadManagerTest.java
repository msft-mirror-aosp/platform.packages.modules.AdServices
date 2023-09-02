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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlagsForTest;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class EnrollmentDataDownloadManagerTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_ENROLLMENT_DATA_FILE_PATH =
            "enrollment/adtech_enrollment_data.csv";
    private MockitoSession mSession = null;
    private EnrollmentDataDownloadManager mEnrollmentDataDownloadManager;

    @Mock private SynchronousFileStorage mMockFileStorage;

    @Mock private EnrollmentDao mMockEnrollmentDao;

    @Mock private ClientFileGroup mMockFileGroup;

    @Mock private ClientFile mMockFile;

    @Mock private MobileDataDownload mMockMdd;
    @Mock private AdServicesLogger mLogger;
    @Mock private EnrollmentUtil mEnrollmentUtil;

    @Mock private Flags mMockFlags;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(FlagsFactory.class)
                    .mockStatic(MobileDataDownloadFactory.class)
                    .mockStatic(EnrollmentDao.class)
                    .spyStatic(ErrorLogUtil.class)
                    .build();

    @After
    public void cleanup() {
        sContext.getSharedPreferences("enrollment_data_read_status", 0).edit().clear().commit();
    }

    @Test
    public void testGetInstance() {
        mockGetFlagsForTest();
        EnrollmentDataDownloadManager firstInstance =
                EnrollmentDataDownloadManager.getInstance(sContext);
        EnrollmentDataDownloadManager secondInstance =
                EnrollmentDataDownloadManager.getInstance(sContext);

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseSuccess()
            throws IOException, ExecutionException, InterruptedException {
        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(sContext, mMockFlags, mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFileGroup.getBuildId()).thenReturn(1L);
        when(mMockFile.getFileId()).thenReturn("adtech_enrollment_data.csv");
        when(mMockFile.getFileUri()).thenReturn("adtech_enrollment_data.csv");
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);

        ArgumentCaptor<EnrollmentData> captor = ArgumentCaptor.forClass(EnrollmentData.class);

        doReturn(true).when(mMockEnrollmentDao).insert(captor.capture());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);

        verify(mMockEnrollmentDao, times(5)).insert(any());
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseFileGroupNull()
            throws ExecutionException, InterruptedException {
        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));

        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        sContext, FlagsFactory.getFlagsForTest(), mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, times(0)).insert(any());
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseEnrollmentDataFileIdMissing()
            throws ExecutionException, InterruptedException, IOException {
        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        sContext, FlagsFactory.getFlagsForTest(), mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn("wrong_file_id.csv");

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, times(0)).insert(any());
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseExecutionException()
            throws ExecutionException, InterruptedException, IOException {
        doNothingOnErrorLogUtilError();

        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(
                        sContext, FlagsFactory.getFlagsForTest(), mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any()))
                .thenReturn(Futures.immediateFailedFuture(new CancellationException()));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn("adtech_enrollment_data.csv");

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.NO_FILE_AVAILABLE);

        verify(mMockEnrollmentDao, times(0)).insert(any());
        verifyZeroInteractions(mLogger);

        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT,
                /* numberOfInvocations= */ 1);
    }

    @Test
    public void testReadFileAndInsertIntoDatabaseParsingFailed()
            throws IOException, ExecutionException, InterruptedException {
        doNothingOnErrorLogUtilError();

        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any())).thenThrow(new IOException());
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(sContext, mMockFlags, mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFile.getFileId()).thenReturn("adtech_enrollment_data.csv");
        when(mMockFile.getFileUri()).thenReturn("adtech_enrollment_data.csv");
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(false);

        ArgumentCaptor<EnrollmentData> captor = ArgumentCaptor.forClass(EnrollmentData.class);

        doReturn(true).when(mMockEnrollmentDao).insert(captor.capture());

        verifyEnrollmentDataDownloadStatus(
                EnrollmentDataDownloadManager.DownloadStatus.PARSING_FAILED);

        verify(mMockEnrollmentDao, times(0)).insert(any());

        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT,
                /* numberOfInvocations= */ 1);
    }

    @Test
    public void testEnrollmentMddRecordDeletionCallsOverwrite()
            throws IOException, ExecutionException, InterruptedException {
        doReturn(mMockFileStorage).when(() -> (MobileDataDownloadFactory.getFileStorage(any())));
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any(), any())));
        doReturn(mMockEnrollmentDao).when(() -> (EnrollmentDao.getInstance(any())));
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(TEST_ENROLLMENT_DATA_FILE_PATH));
        mEnrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(sContext, mMockFlags, mLogger, mEnrollmentUtil);

        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileGroup.getFileList()).thenReturn(Collections.singletonList(mMockFile));
        when(mMockFileGroup.getBuildId()).thenReturn(1L);
        when(mMockFile.getFileId()).thenReturn("adtech_enrollment_data.csv");
        when(mMockFile.getFileUri()).thenReturn("adtech_enrollment_data.csv");

        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(true);

        ArgumentCaptor<EnrollmentData> captor = ArgumentCaptor.forClass(EnrollmentData.class);

        doReturn(true).when(mMockEnrollmentDao).overwriteData(any());

        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SUCCESS);

        verify(mMockEnrollmentDao, times(1)).overwriteData(any());

        verify(mMockEnrollmentDao, times(0)).insert(any());

        verify(mEnrollmentUtil, times(1))
                .logEnrollmentFileDownloadStats(eq(mLogger), eq(true), eq("1"));

        // Verify no duplicate inserts after enrollment data is saved before.
        verifyEnrollmentDataDownloadStatus(EnrollmentDataDownloadManager.DownloadStatus.SKIP);
        verifyZeroInteractions(mMockEnrollmentDao);
        verifyZeroInteractions(mEnrollmentUtil);
    }

    private void verifyEnrollmentDataDownloadStatus(
            EnrollmentDataDownloadManager.DownloadStatus status)
            throws InterruptedException, ExecutionException {
        assertThat(mEnrollmentDataDownloadManager.readAndInsertEnrollmentDataFromMdd().get())
                .isEqualTo(status);
    }
}
