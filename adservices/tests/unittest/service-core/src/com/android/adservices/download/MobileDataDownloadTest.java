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
import static com.android.adservices.service.topics.classifier.ModelManager.BUNDLED_CLASSIFIER_ASSETS_METADATA_PATH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

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
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.CommonClassifierHelper;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Unit tests for {@link MobileDataDownloadFactory} */
@RunWith(Parameterized.class)
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
    private static final String MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/1388/ad579473d45d783185b03445edf594a0a404a819";
    private static final String MDD_ENROLLMENT_MANIFEST_FILE_URL =
            "https://dl.google.com/mdi-serving/adservices/adtech_enrollment/manifest_configs/1/manifest_config_1658790241927.binaryproto";
    // Prod Test Bed enrollment manifest URL
    private static final String PTB_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1281/a245b0927ba27b3d954b0ca2775651ccfc9a5e84";
    private static final String UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/1360/d428721d225582922a7fe9d5ad6db7b09cb03209";
    private static final String OEM_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1304/acd369267b5d25e377bc78a258a4e5e749b91e72";

    public static final String TEST_TOPIC_FILE_GROUP_NAME = "topics-classifier-model";
    public static final String ENROLLMENT_FILE_GROUP_NAME = "adtech_enrollment_data";
    public static final String UI_OTA_STRINGS_FILE_GROUP_NAME = "ui-ota-strings";

    private StaticMockitoSession mStaticMockSession = null;
    private SynchronousFileStorage mFileStorage;
    private FileDownloader mFileDownloader;
    private DbHelper mDbHelper;
    private MobileDataDownload mMdd;

    @Parameterized.Parameter(0)
    public String enrollmentUrl;

    @Parameterized.Parameter(1)
    public int numOfEntries;

    @Parameterized.Parameter(2)
    public int fileGroupVersion;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {MDD_ENROLLMENT_MANIFEST_FILE_URL, 5, 1},
                    {PTB_ENROLLMENT_MANIFEST_FILE_URL, 1, 0},
                    {OEM_ENROLLMENT_MANIFEST_FILE_URL, 33, 0}
                });
    }

    @Mock Flags mMockFlags;
    @Mock ConsentManager mConsentManager;

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
                        .spyStatic(ConsentManager.class)
                        .spyStatic(CommonClassifierHelper.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        doReturn(/* Download max download threads */ 2)
                .when(mMockFlags)
                .getDownloaderMaxDownloadThreads();

        doReturn(/* Default value */ false).when(mMockFlags).getEnableTopicMigration();

        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        mFileDownloader =
                MobileDataDownloadFactory.getFileDownloader(mContext, mMockFlags, mFileStorage);

        mDbHelper = DbTestUtil.getDbHelperForTest();

        when(mConsentManager.getConsent()).thenReturn(AdServicesApiConsent.GIVEN);
        // Mock static method ConsentManager.getInstance() to return test ConsentManager
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
        if (mMdd != null) {
            mMdd.clear().get();
        }
    }

    @Test
    public void testCreateMddManagerSuccessfully() throws ExecutionException, InterruptedException {
        mMdd =
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
                        mMdd.addFileGroup(
                                        AddFileGroupRequest.newBuilder()
                                                .setDataFileGroup(dataFileGroup)
                                                .build())
                                .get())
                .isTrue();

        // Trigger the download immediately.
        ClientFileGroup clientFileGroup =
                mMdd.downloadFileGroup(
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

    @Test
    public void testTopicsManifestFileGroupPopulator_ManifestConfigOverrider_NoFileGroup()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForTopics(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
        // The server side test model build_id = 1388, which equals to bundled model build_id =
        // 1388. ManifestConfigOverrider will not add the DataFileGroup in the
        // TopicsManifestFileGroupPopulator and will not download either.
        assertThat(
                        mMdd.getFileGroup(
                                        GetFileGroupRequest.newBuilder()
                                                .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                                .build())
                                .get())
                .isNull();
    }

    /**
     * This method tests topics manifest files. It downloads test classifier model and verifies
     * files downloaded successfully.
     */
    @Test
    public void testTopicsManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Set the bundled build_id to 1 so the server side build_id will be bigger. This will
        // trigger MDD download.
        ExtendedMockito.doReturn(1L)
                .when(
                        () ->
                                CommonClassifierHelper.getBundledModelBuildId(
                                        mContext, BUNDLED_CLASSIFIER_ASSETS_METADATA_PATH));

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
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
     * This method tests enrollment data, verifies files downloaded successfully and data saved into
     * DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForEnrollment(enrollmentUrl);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify measurement file group
        assertThat(clientFileGroup.getGroupName()).isEqualTo(ENROLLMENT_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getVersionNumber()).isEqualTo(fileGroupVersion);

        ExtendedMockito.doReturn(mMdd)
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
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(numOfEntries);
        assertThat(enrollmentDao.deleteAll()).isTrue();
    }

    /**
     * This method tests enrollment data, verifies that the file group doesn't exist if the consent
     * is revoked.
     */
    @Test
    public void testEnrollmentDataDownloadFailOnConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForEnrollment(enrollmentUrl);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * This method tests enrollment data, verifies that the file group exists if the consent is
     * given.
     */
    @Test
    public void testEnrollmentDataDownloadOnConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForEnrollment(enrollmentUrl);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests topics data, verifies that the file group doesn't exist if the consent is
     * revoked.
     */
    @Test
    public void testMddTopicsFailsOnConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * This method tests topics data, verifies that the file group exists if the consent is given.
     */
    @Test
    public void testMddTopicsOnConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        ExtendedMockito.doReturn(1L)
                .when(
                        () ->
                                CommonClassifierHelper.getBundledModelBuildId(
                                        mContext, BUNDLED_CLASSIFIER_ASSETS_METADATA_PATH));

        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests OTA data, verifies that the file group exists if the consent is given to at
     * least one of the APIs.
     */
    @Test
    public void testOtaOnTopicsConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        android.util.Log.i("adservices", "12334");
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForUiOTAString(UI_OTA_STRINGS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests OTA data, verifies that the file group doesn't exist if the consent is
     * revoked for all the APIs.
     */
    @Test
    public void testOtaFailsOnAggregatedConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.REVOKED);
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.REVOKED);
        when(mConsentManager.getConsent(AdServicesApiType.FLEDGE))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
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
        createMddForUiOTAString(UI_OTA_STRINGS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
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
    private void createMddForEnrollment(String enrollmentManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(enrollmentManifestFileUrl).when(mMockFlags).getMeasurementManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getMeasurementManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains Measurement
        // FileGroupPopulator only.

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in topics manifest url.
    @NonNull
    private void createMddForTopics(String topicsManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(topicsManifestFileUrl).when(mMockFlags).getMddTopicsClassifierManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getTopicsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains Topics
        // FileGroupPopulator only.

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in UI OTA String manifest url.
    @NonNull
    private void createMddForUiOTAString(String uiOtaStringManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(uiOtaStringManifestFileUrl).when(mMockFlags).getUiOtaStringsManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getUiOtaStringsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        ImmutableList.<FileGroupPopulator>builder()
                                .add(fileGroupPopulator)
                                .build()); // List of FileGroupPopulator that contains UI OTA String
        // FileGroupPopulator only.

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    private long getNumEntriesInEnrollmentTable() {
        return DatabaseUtils.queryNumEntries(
                mDbHelper.getReadableDatabase(),
                EnrollmentTables.EnrollmentDataContract.TABLE,
                null);
    }
}
