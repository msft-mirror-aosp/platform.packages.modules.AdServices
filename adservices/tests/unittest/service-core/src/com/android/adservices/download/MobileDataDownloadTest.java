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

import static com.android.adservices.download.EnrollmentDataDownloadManager.DownloadStatus.SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.database.DatabaseUtils;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import com.google.android.libraries.mobiledatadownload.AddFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.FileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TaskScheduler;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DataFile;
import com.google.mobiledatadownload.DownloadConfigProto.DataFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Unit tests for {@link MobileDataDownloadFactory} */
@SmallTest
public class MobileDataDownloadTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final int MAX_HANDLE_TASK_WAIT_TIME_SECS = 300;

    private static final String FILE_GROUP_NAME_1 = "test-group-1";
    private static final String FILE_GROUP_NAME_2 = "test-group-2";
    private static final String FILE_ID_1 = "test-file-1";
    private static final String FILE_ID_2 = "test-file-2";
    private static final String FILE_CHECKSUM_1 = "c1ef7864c76a99ae738ddad33882ed65972c99cc";
    private static final String FILE_URL_1 = "https://www.gstatic.com/suggest-dev/odws1_test_4.jar";
    private static final int FILE_SIZE_1 = 85769;

    private static final String FILE_CHECKSUM_2 = "a1cba9d87b1440f41ce9e7da38c43e1f6bd7d5df";
    private static final String FILE_URL_2 = "https://www.gstatic.com/suggest-dev/odws1_empty.jar";
    private static final int FILE_SIZE_2 = 554;

    // TODO(b/263521464): Use the production topics classifier manifest URL.
    private static final String TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/922/217081737fd739c74dd3ca5c407813d818526577";
    private static final String MDD_ENROLLMENT_MANIFEST_FILE_URL =
            "https://dl.google.com/mdi-serving/adservices/adtech_enrollment/manifest_configs/1/manifest_config_1658790241927.binaryproto";
    // Prod Test Bed enrollment manifest URL
    private static final String PTB_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1281/a245b0927ba27b3d954b0ca2775651ccfc9a5e84";
    private static final String UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/1360/d428721d225582922a7fe9d5ad6db7b09cb03209";
    public static final String TEST_TOPIC_FILE_GROUP_NAME = "topics-classifier-model";
    public static final String ENROLLMENT_FILE_GROUP_NAME = "adtech_enrollment_data";
    public static final String UI_OTA_STRINGS_FILE_GROUP_NAME = "ui-ota-strings";

    private StaticMockitoSession mStaticMockSession = null;
    private SynchronousFileStorage mFileStorage;
    private FileDownloader mFileDownloader;
    private DbHelper mDbHelper;

    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method.
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(MddLogger.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MobileDataDownloadFactory.class)
                        .spyStatic(EnrollmentDao.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        doReturn(/* Download max download threads */ 2)
                .when(mMockFlags)
                .getDownloaderMaxDownloadThreads();

        doReturn(/* Default value */ false).when(mMockFlags).getEnableDatabaseSchemaVersion5();

        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        mFileDownloader =
                MobileDataDownloadFactory.getFileDownloader(mContext, mMockFlags, mFileStorage);

        mDbHelper = DbTestUtil.getDbHelperForTest();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testCreateMddManagerSuccessfully() throws ExecutionException, InterruptedException {
        MobileDataDownload mdd =
                getMddForTesting(
                        mContext,
                        FlagsFactory.getFlagsForTest(),
                        ImmutableList.<FileGroupPopulator>builder()
                                .build()); // Pass in an empty list of FileGroupPopulator. Add ad
        // hoc datafilegroup to MDD manually below.

        DataFileGroup dataFileGroup =
                createDataFileGroup(
                        FILE_GROUP_NAME_1,
                        mContext.getPackageName(),
                        5 /* versionNumber */,
                        new String[] {FILE_ID_1, FILE_ID_2},
                        new int[] {FILE_SIZE_1, FILE_SIZE_2},
                        new String[] {FILE_CHECKSUM_1, FILE_CHECKSUM_2},
                        new String[] {FILE_URL_1, FILE_URL_2},
                        DeviceNetworkPolicy.DOWNLOAD_ONLY_ON_WIFI);
        // Add the DataFileGroup to MDD
        assertThat(
                        mdd.addFileGroup(
                                        AddFileGroupRequest.newBuilder()
                                                .setDataFileGroup(dataFileGroup)
                                                .build())
                                .get())
                .isTrue();

        // Trigger the download immediately.
        ClientFileGroup clientFileGroup =
                mdd.downloadFileGroup(
                                DownloadFileGroupRequest.newBuilder()
                                        .setGroupName(FILE_GROUP_NAME_1)
                                        .build())
                        .get();

        // Verify the downloaded DataFileGroup.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(FILE_GROUP_NAME_1);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getVersionNumber()).isEqualTo(5);
        assertThat(clientFileGroup.getFileCount()).isEqualTo(2);
        assertThat(clientFileGroup.hasAccount()).isFalse();
    }

    /**
     * This method tests topics manifest files. It downloads test classifier model and verifies
     * files downloaded successfully.
     */
    @Test
    public void testTopicsManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Return a manifest URL for test only. This will download smaller size files only for
        // testing MDD download feature.
        doReturn(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getMddTopicsClassifierManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getTopicsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        MobileDataDownload mdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains Topics
        // FileGroupPopulator only.

        mdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);

        ClientFileGroup clientFileGroup =
                mdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify topics file group.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(TEST_TOPIC_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getVersionNumber())
                .isEqualTo(/* Test filegroup version number */ 0);
        assertThat(clientFileGroup.getFileCount()).isEqualTo(6);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getBuildId()).isEqualTo(/* BuildID generated by Ingress */ 922);
    }

    /**
     * This method tests measurement manifest files. It downloads production adtech enrollment data,
     * verifies files downloaded successfully and data saved into DB correctly.
     */
    @Test
    public void testEnrollmentManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        MobileDataDownload mdd = getMddForEnrollment(MDD_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify measurement file group
        assertThat(clientFileGroup.getGroupName()).isEqualTo(ENROLLMENT_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getVersionNumber())
                .isEqualTo(/* Measurement filegroup version number */ 1);

        ExtendedMockito.doReturn(mdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        EnrollmentDataDownloadManager enrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(mContext, mMockFlags);

        EnrollmentDao enrollmentDao = new EnrollmentDao(mContext, mDbHelper);

        ExtendedMockito.doReturn(enrollmentDao)
                .when(() -> EnrollmentDao.getInstance(any(Context.class)));

        assertThat(enrollmentDao.deleteAll()).isTrue();
        // Verify no enrollment data after table cleared.
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(0);
        // Verify enrollment data file read from MDD and insert the data into the enrollment
        // database.
        assertThat(enrollmentDataDownloadManager.readAndInsertEnrolmentDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // 5 enrollment records added from the MDD data file.
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(5);
        assertThat(enrollmentDao.deleteAll()).isTrue();
    }

    /**
     * This method tests Prod Test Bed measurement manifest files. It downloads Prod Test Bed adtech
     * enrollment data, verifies files downloaded successfully and data saved into DB correctly.
     */
    @Test
    public void testPtbEnrollmentManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        MobileDataDownload mdd = getMddForEnrollment(PTB_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify measurement file group
        assertThat(clientFileGroup.getGroupName()).isEqualTo(ENROLLMENT_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getVersionNumber())
                .isEqualTo(/* PTB Measurement filegroup version number */ 0);

        ExtendedMockito.doReturn(mdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        EnrollmentDataDownloadManager enrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(mContext, mMockFlags);
        EnrollmentDao enrollmentDao = new EnrollmentDao(mContext, mDbHelper);

        ExtendedMockito.doReturn(enrollmentDao)
                .when(() -> EnrollmentDao.getInstance(any(Context.class)));

        assertThat(enrollmentDao.deleteAll()).isTrue();
        // Verify no enrollment data after table cleared.
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(0);
        // Verify enrollment data file read from MDD and insert the data into the enrollment
        // database.
        assertThat(enrollmentDataDownloadManager.readAndInsertEnrolmentDataFromMdd().get())
                .isEqualTo(SUCCESS);
        // 1 enrollment record added from the PTB MDD data file.
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(1);
        assertThat(enrollmentDao.deleteAll()).isTrue();
    }

    @Test
    public void testMddLoggerKillSwitchIsOn() {
        // Killswitch is on. MddLogger should be disabled.
        doReturn(true).when(mMockFlags).getMddLoggerKillSwitch();
        Optional<Logger> mddLogger = MobileDataDownloadFactory.getMddLogger(mMockFlags);
        assertThat(mddLogger).isAbsent();
    }

    @Test
    public void testMddLoggerKillSwitchIsOff() {
        // Killswitch is off. MddLogger should be enabled.
        doReturn(false).when(mMockFlags).getMddLoggerKillSwitch();
        Optional<Logger> mddLogger = MobileDataDownloadFactory.getMddLogger(mMockFlags);
        assertThat(mddLogger).isPresent();
    }

    /**
     * This method tests UI OTA Strings manifest files. It downloads test UI Strings file and
     * verifies files downloaded successfully.
     */
    @Test
    public void testUiOtaStringsManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Return test UI OTA strings file url.
        doReturn(UI_OTA_STRINGS_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getUiOtaStringsManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getUiOtaStringsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        MobileDataDownload mdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains UI
        // FileGroupPopulator only.

        mdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);

        ClientFileGroup clientFileGroup =
                mdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify UI file group.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(UI_OTA_STRINGS_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getBuildId()).isEqualTo(/* BuildID generated by Ingress */ 1360);
    }

    // A helper function to create a DataFilegroup.
    private static DataFileGroup createDataFileGroup(
            String groupName,
            String ownerPackage,
            int versionNumber,
            String[] fileId,
            int[] byteSize,
            String[] checksum,
            String[] url,
            DeviceNetworkPolicy deviceNetworkPolicy) {
        if (fileId.length != byteSize.length
                || fileId.length != checksum.length
                || fileId.length != url.length) {
            throw new IllegalArgumentException();
        }

        DataFileGroup.Builder dataFileGroupBuilder =
                DataFileGroup.newBuilder()
                        .setGroupName(groupName)
                        .setOwnerPackage(ownerPackage)
                        .setFileGroupVersionNumber(versionNumber)
                        .setDownloadConditions(
                                DownloadConditions.newBuilder()
                                        .setDeviceNetworkPolicy(deviceNetworkPolicy));

        for (int i = 0; i < fileId.length; ++i) {
            DataFile file =
                    DataFile.newBuilder()
                            .setFileId(fileId[i])
                            .setByteSize(byteSize[i])
                            .setChecksum(checksum[i])
                            .setUrlToDownload(url[i])
                            .build();
            dataFileGroupBuilder.addFile(file);
        }

        return dataFileGroupBuilder.build();
    }

    /**
     * Returns a MobileDataDownload instance for testing.
     *
     * @param context the context
     * @param flags the flags
     * @param fileGroupPopulators a list of FileGroupPopulator that will be added to the MDD
     * @return a MobileDataDownload instance.
     */
    @NonNull
    private MobileDataDownload getMddForTesting(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull ImmutableList<FileGroupPopulator> fileGroupPopulators) {
        context = context.getApplicationContext();
        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(context);
        FileDownloader fileDownloader =
                MobileDataDownloadFactory.getFileDownloader(context, flags, fileStorage);
        NetworkUsageMonitor networkUsageMonitor =
                new NetworkUsageMonitor(context, System::currentTimeMillis);

        return MobileDataDownloadBuilder.newBuilder()
                .setContext(context)
                .setControlExecutor(MobileDataDownloadFactory.getControlExecutor())
                .setNetworkUsageMonitor(networkUsageMonitor)
                .setFileStorage(fileStorage)
                .setFileDownloaderSupplier(() -> fileDownloader)
                .addFileGroupPopulators(fileGroupPopulators)
                .setLoggerOptional(MobileDataDownloadFactory.getMddLogger(flags))
                // Use default MDD flags so that it does not need to access DeviceConfig
                // which is inaccessible from Unit Tests.
                .setFlagsOptional(
                        Optional.of(new com.google.android.libraries.mobiledatadownload.Flags() {}))
                .build();
    }

    // Returns MobileDataDownload using passed in enrollment manifest url.
    @NonNull
    private MobileDataDownload getMddForEnrollment(String enrollmentManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(enrollmentManifestFileUrl).when(mMockFlags).getMeasurementManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getMeasurementManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        MobileDataDownload mdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains Measurement
        // FileGroupPopulator only.

        mdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
        return mdd;
    }

    private long getNumEntriesInEnrollmentTable() {
        return DatabaseUtils.queryNumEntries(
                mDbHelper.getReadableDatabase(),
                EnrollmentTables.EnrollmentDataContract.TABLE,
                null);
    }
}
