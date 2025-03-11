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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.database.DatabaseUtils;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.CommonClassifierHelper;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.testing.network.NetworkConnectionHelper;
import com.android.adservices.shared.util.Clock;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.AddFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.FileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TaskScheduler;
import com.google.android.libraries.mobiledatadownload.TimeSource;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.mobiledatadownload.ClientConfigProto;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DataFile;
import com.google.mobiledatadownload.DownloadConfigProto.DataFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Unit tests for {@link MobileDataDownloadFactory} */
@SpyStatic(MddLogger.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MobileDataDownloadFactory.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(EncryptionKeyDao.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(CommonClassifierHelper.class)
public final class MobileDataDownloadTest extends AdServicesExtendedMockitoTestCase {
    private static final int MAX_HANDLE_TASK_WAIT_TIME_SECS = 300;

    // Two files are from cts_test_1 folder.
    // https://source.corp.google.com/piper///depot/google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
    private static final String FILE_GROUP_NAME_1 = "test-group-1";
    private static final String FILE_ID_1 = "classifier_assets_metadata.json";
    private static final String FILE_ID_2 = "stopwords.txt";
    private static final String FILE_CHECKSUM_1 = "52633ae715ead32ec6c8ae721ad34ea301336a8e";
    private static final String FILE_URL_1 =
            "https://dl.google.com/mdi-serving/rubidium-adservices-topics-classifier/1489"
                    + "/52633ae715ead32ec6c8ae721ad34ea301336a8e";
    private static final int FILE_SIZE_1 = 1026;

    private static final String FILE_CHECKSUM_2 = "042dc4512fa3d391c5170cf3aa61e6a638f84342";
    private static final String FILE_URL_2 =
            "https://dl.google.com/mdi-serving/rubidium-adservices-topics-classifier/1489"
                    + "/042dc4512fa3d391c5170cf3aa61e6a638f84342";
    private static final int FILE_SIZE_2 = 1;

    // TODO(b/263521464): Use the production topics classifier manifest URL.
    private static final String TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/922"
                    + "/217081737fd739c74dd3ca5c407813d818526577";
    private static final String MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/1986"
                    + "/9e98784bcdb26a3eb2ab3f65ee811f43177c761f";
    private static final String PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/4503"
                    + "/fecd522d3dcfbe1b3b1f1054947be8528be43e97";
    private static final String PRODUCTION_ENCRYPTION_KEYS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-encryption-keys/4543"
                    + "/e9d118728752e6a6bfb5d7d8d1520807591f0717";

    private static final String MDD_COBALT_REGISTRY_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/cobalt-rubidium-registry/6793"
                    + "/64988540ea376f9fb690ddb92b2e57be6eb516d8";

    // Prod Test Bed enrollment manifest URL
    private static final String PTB_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/3548"
                    + "/206afe932d6db2a87cad70421454a0c258297d77";
    private static final String OEM_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1760"
                    + "/1460e6aea598fe7a153100d6e2749f45313ef905";
    private static final String UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/1360"
                    + "/d428721d225582922a7fe9d5ad6db7b09cb03209";

    private static final String UI_OTA_RESOURCES_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/3150"
                    + "/672c83fa4aad630a360dc3b7ce43d94ab75852cd";

    private static final int PRODUCTION_ENROLLMENT_ENTRIES = 78;

    /** Old PTB URL with a small number of enrollment records. */
    private static final String PTB_OLD_ENROLLMENT_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/3156"
                    + "/9d9d99be0c6dc71fc329f5c02a0fac48d3b06e73";

    private static final String MDD_PACKAGE_DENY_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-package-deny-list/6424"
                    + "/1d22cb7e5e7b0ca9a7e1cc16c0354781f8b8ba55";
    private static final int PTB_ENROLLMENT_ENTRIES = 6;

    private static final int PTB_OLD_ENROLLMENT_ENTRIES = 4;
    private static final int OEM_ENROLLMENT_ENTRIES = 114;

    private static final int PRODUCTION_FILEGROUP_VERSION = 0;
    private static final int PTB_FILEGROUP_VERSION = 0;
    private static final int OEM_FILEGROUP_VERSION = 0;

    public static final String TEST_TOPIC_FILE_GROUP_NAME = "topics-classifier-model";
    public static final String ENCRYPTION_KEYS_FILE_GROUP_NAME = "encryption-keys";
    public static final String ENROLLMENT_FILE_GROUP_NAME = "adtech_enrollment_data";
    public static final String ENROLLMENT_PROTO_FILE_GROUP_NAME = "adtech_enrollment_proto_data";
    public static final String UI_OTA_STRINGS_FILE_GROUP_NAME = "ui-ota-strings";
    public static final String COBALT_REGISTRY_FILE_GROUP_NAME = "rubidium-registry";
    public static final String PACKAGE_DENY_FILE_GROUP_NAME = "package-deny";

    private SynchronousFileStorage mFileStorage;
    private FileDownloader mFileDownloader;
    private SharedDbHelper mDbHelper;
    private MobileDataDownload mMdd;

    @Mock private ConsentManager mConsentManager;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private Clock mMockClock;

    @Before
    public void setUp() throws Exception {
        // The MDD integration tests require wifi connection. If the running device is not
        // connected to the Wifi, tests should be skipped.
        Assume.assumeTrue(
                "Device must have wifi connection",
                NetworkConnectionHelper.isWifiConnected(mContext));

        mockMddFlags();

        mFileStorage = MobileDataDownloadFactory.getFileStorage();
        mFileDownloader = MobileDataDownloadFactory.getFileDownloader(mMockFlags, mFileStorage);

        mDbHelper = DbTestUtil.getSharedDbHelperForTest();

        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        // Mock static method ConsentManager.getInstance() to return test ConsentManager
        doReturn(mConsentManager).when(ConsentManager::getInstance);
        doReturn(mUxStatesManager).when(UxStatesManager::getInstance);
        overridingMddLoggingLevel("VERBOSE");
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        if (mMdd != null) {
            mMdd.clear().get();
        }
        overridingMddLoggingLevel("INFO");
    }

    @Test
    public void testCreateMddManagerSuccessfully() throws ExecutionException, InterruptedException {
        mMdd =
                getMddForTesting(
                        mContext,
                        FakeFlagsFactory.getFlagsForTest(),
                        // Pass in an empty list of FileGroupPopulator. Add ad hoc DataFileGroup
                        // to MDD manually below.
                        ImmutableList.of());

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
        expect.that(clientFileGroup.getGroupName()).isEqualTo(FILE_GROUP_NAME_1);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getVersionNumber()).isEqualTo(5);
        expect.that(clientFileGroup.getFileCount()).isEqualTo(2);
        expect.that(clientFileGroup.hasAccount()).isFalse();
    }

    @Test
    public void testTopicsManifestFileGroupPopulator_ManifestConfigOverrider_NoFileGroup()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForTopics(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
        // The server side test model build_id = 1986, which equals to bundled model build_id =
        // 1986. ManifestConfigOverrider will not add the DataFileGroup in the
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
        doReturn(1L).when(() -> CommonClassifierHelper.getBundledModelBuildId(any(), any()));

        createMddForTopics(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify topics file group.
        expect.that(clientFileGroup.getGroupName()).isEqualTo(TEST_TOPIC_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getVersionNumber())
                .isEqualTo(/* Test filegroup version number */ 0);
        expect.that(clientFileGroup.getFileCount()).isEqualTo(6);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getBuildId())
                .isEqualTo(/* BuildID generated by Ingress */ 1986);
    }

    /**
     * This method tests MDD production encryption keys data, verifies files downloaded successfully
     * and data saved into DB correctly.
     */
    @Test
    public void testEncryptionKeysDataDownload_production_featureEnabled() throws Exception {
        doReturn(true).when(mMockFlags).getEnableMddEncryptionKeys();
        // NOTE: it looks like the statement below is not needed anymore - this tests would pass
        // even if it's removed
        // All keys have greater expiration time than this timestamp. (Sep 2, 1996)
        mocker.mockCurrentTimeMillis(mMockClock, 841622400000L);
        createMddForEncryptionKeys(PRODUCTION_ENCRYPTION_KEYS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENCRYPTION_KEYS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
        verifyEncryptionKeysFileGroup(clientFileGroup, /* NumberOfKeysOnTestUrl */ 2);
    }

    /** Test disabling the feature flag does not create the manifest for download. */
    @Test
    public void testEncryptionKeysDataDownload_production_featureDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getEnableMddEncryptionKeys();
        createMddForEncryptionKeys(PRODUCTION_ENCRYPTION_KEYS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENCRYPTION_KEYS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * This method tests MDD production enrollment data, verifies files downloaded successfully and
     * data saved into DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload_Production()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(true);
        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL, /* getProto= */ false);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(
                clientFileGroup, PRODUCTION_FILEGROUP_VERSION, PRODUCTION_ENROLLMENT_ENTRIES);
    }

    /**
     * This method tests OEM enrollment data, verifies files downloaded successfully and data saved
     * into DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload_OEM()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(true);
        createMddForEnrollment(OEM_ENROLLMENT_MANIFEST_FILE_URL, /* getProto= */ false);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(clientFileGroup, OEM_FILEGROUP_VERSION, OEM_ENROLLMENT_ENTRIES);
    }

    /**
     * This method tests Prod Test Bed enrollment data, verifies files downloaded successfully and
     * data saved into DB correctly, additionally checks record deletion when flag is enabled.
     */
    @Test
    public void testEnrollmentDataDownload_PTB()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(true);
        when(mMockFlags.getEnrollmentMddRecordDeletionEnabled()).thenReturn(true);
        createMddForEnrollment(PTB_ENROLLMENT_MANIFEST_FILE_URL, /* getProto= */ false);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(
                clientFileGroup,
                PTB_FILEGROUP_VERSION,
                PTB_ENROLLMENT_ENTRIES,
                /* clearExistingData= */ true,
                /* clearDownloadedData= */ false);
        createMddForEnrollment(PTB_OLD_ENROLLMENT_FILE_URL, /* getProto= */ false);
        verifyMeasurementFileGroup(
                clientFileGroup,
                PTB_FILEGROUP_VERSION,
                PTB_OLD_ENROLLMENT_ENTRIES,
                /* clearExistingData= */ false,
                /* clearDownloadedData= */ true);
    }

    // TODO (b/340891475): Add tests for Enrollment production proto files

    /** This method verifies that the file group does not exist when an empty url is provided. */
    @Test
    public void testEnrollmentProtoDataDownload_emptyUrl() throws Exception {
        when(mMockFlags.getEncryptionKeyNewEnrollmentFetchKillSwitch()).thenReturn(true);
        createMddForEnrollment("", /* getProto= */ true);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_PROTO_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
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

        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL, /* getProto= */ false);

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

        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL, /* getProto= */ false);

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
        doReturn(1L).when(() -> CommonClassifierHelper.getBundledModelBuildId(any(), any()));

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
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        doReturn(UI_OTA_STRINGS_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getUiOtaStringsManifestFileUrl();
        doReturn(UI_OTA_RESOURCES_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getUiOtaResourcesManifestFileUrl();
        createMddForUiOTA();

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

    // Topics MFGP should be disabled for U18 UX.
    @Test
    public void topicsDownloadTest_U18UxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(PrivacySandboxUxCollection.U18_UX).when(mUxStatesManager).getUx();

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    @Test
    public void testMddLoggerFeatureFlagIsOff() {
        // The feature flag is off. MddLogger should be disabled.
        doReturn(false).when(mMockFlags).getMddLoggerEnabled();
        Optional<Logger> mddLogger = MobileDataDownloadFactory.getMddLogger(mMockFlags);
        assertThat(mddLogger).isAbsent();
    }

    @Test
    public void testMddLoggerFeatureFlagIsOn() {
        // The feature flag is on. MddLogger should be enabled.
        doReturn(true).when(mMockFlags).getMddLoggerEnabled();
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
        doReturn(false).when(mMockFlags).getUiOtaResourcesFeatureEnabled();
        doReturn(UI_OTA_STRINGS_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getUiOtaStringsManifestFileUrl();
        createMddForUiOTA();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify UI file group.
        expect.that(clientFileGroup.getGroupName()).isEqualTo(UI_OTA_STRINGS_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getFileCount()).isEqualTo(1);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getBuildId())
                .isEqualTo(/* BuildID generated by Ingress */ 1360);
    }

    /**
     * This method tests UI OTA resources manifest files. It downloads test UI apk file and verifies
     * files downloaded successfully.
     */
    @Test
    public void testUiOtaResourcesManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getUiOtaResourcesFeatureEnabled();
        doReturn(UI_OTA_RESOURCES_MANIFEST_FILE_URL)
                .when(mMockFlags)
                .getUiOtaResourcesManifestFileUrl();
        createMddForUiOTA();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify UI file group.
        expect.that(clientFileGroup.getGroupName()).isEqualTo(UI_OTA_STRINGS_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getFileCount()).isEqualTo(1);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getBuildId())
                .isEqualTo(/* BuildID generated by Ingress */ 3150);
    }

    /**
     * Tests Cobalt registry file group download successfully when Cobalt registry out-of-band
     * update feature is enabled.
     */
    @Test
    public void testCobaltRegistryManifestFileGroupPopulator_featureEnabled() throws Exception {
        when(mMockFlags.getCobaltRegistryOutOfBandUpdateEnabled()).thenReturn(true);
        when(mMockFlags.getMddCobaltRegistryManifestFileUrl())
                .thenReturn(MDD_COBALT_REGISTRY_MANIFEST_FILE_URL);

        createMddForCobaltRegistry();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(COBALT_REGISTRY_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify Cobalt registry file group.
        expect.that(clientFileGroup.getGroupName()).isEqualTo(COBALT_REGISTRY_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getFileCount()).isEqualTo(1);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getBuildId())
                .isEqualTo(/* BuildID generated by Ingress */ 6793);
    }

    /**
     * Tests no Cobalt registry file group downloaded when Cobalt registry out-of-band update
     * feature is disabled.
     */
    @Test
    public void testCobaltRegistryManifestFileGroupPopulator_featureDisabled() throws Exception {
        when(mMockFlags.getCobaltRegistryOutOfBandUpdateEnabled()).thenReturn(false);
        when(mMockFlags.getMddCobaltRegistryManifestFileUrl())
                .thenReturn(MDD_COBALT_REGISTRY_MANIFEST_FILE_URL);

        createMddForCobaltRegistry();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(COBALT_REGISTRY_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * Tests package deny registry file group download successfully when package deny mdd feature is
     * enabled.
     */
    @Test
    public void testPackageDenyManifestFileGroupPopulator_featureEnabled() throws Exception {
        when(mMockFlags.getEnablePackageDenyMdd()).thenReturn(true);
        when(mMockFlags.getMddPackageDenyRegistryManifestFileUrl())
                .thenReturn(MDD_PACKAGE_DENY_MANIFEST_FILE_URL);

        createMddForPackageDenyRegistry();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(PACKAGE_DENY_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify package deny registry file group.
        expect.that(clientFileGroup.getGroupName()).isEqualTo(PACKAGE_DENY_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getFileCount()).isEqualTo(1);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getBuildId())
                .isEqualTo(/* BuildID generated by Ingress */ 6424);
    }

    /**
     * Tests no package registry file group downloaded when package deny mdd feature is disabled.
     */
    @Test
    public void testPackageDenyManifestFileGroupPopulator_featureDisabled() throws Exception {
        when(mMockFlags.getMddPackageDenyRegistryManifestFileUrl())
                .thenReturn(MDD_PACKAGE_DENY_MANIFEST_FILE_URL);

        createMddForPackageDenyRegistry();

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(PACKAGE_DENY_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
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
    private static MobileDataDownload getMddForTesting(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull ImmutableList<FileGroupPopulator> fileGroupPopulators) {
        context = context.getApplicationContext();
        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage();
        FileDownloader fileDownloader =
                MobileDataDownloadFactory.getFileDownloader(flags, fileStorage);
        NetworkUsageMonitor networkUsageMonitor =
                new NetworkUsageMonitor(
                        context,
                        new TimeSource() {
                            @Override
                            public long currentTimeMillis() {
                                return System.currentTimeMillis();
                            }

                            @Override
                            public long elapsedRealtimeNanos() {
                                return SystemClock.elapsedRealtimeNanos();
                            }
                        });

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

    // Returns MobileDataDownload using passed in encryption keys manifest url.
    private void createMddForEncryptionKeys(String encryptionManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(encryptionManifestFileUrl).when(mMockFlags).getMddEncryptionKeysManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getEncryptionKeysManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains Measurement FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in enrollment manifest url.
    private void createMddForEnrollment(String enrollmentManifestFileUrl, boolean getProto)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (getProto) {
            doReturn(enrollmentManifestFileUrl).when(mMockFlags).getMddEnrollmentManifestFileUrl();
        } else {
            doReturn(enrollmentManifestFileUrl).when(mMockFlags).getMeasurementManifestFileUrl();
        }

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getMeasurementManifestPopulator(
                        mMockFlags, mFileStorage, mFileDownloader, getProto);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains Measurement FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in topics manifest url.
    private void createMddForTopics(String topicsManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(topicsManifestFileUrl).when(mMockFlags).getMddTopicsClassifierManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getTopicsManifestPopulator(
                        mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains Topics FileGroupPopulator only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in UI OTA manifest url.
    private void createMddForUiOTA()
            throws ExecutionException, InterruptedException, TimeoutException {
        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getUiOtaResourcesManifestPopulator(
                        mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains UI OTA String FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in Cobalt registry manifest url.
    private void createMddForCobaltRegistry() throws Exception {
        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getCobaltRegistryManifestPopulator(
                        mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains UI OTA String FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in deny package registry manifest url.
    private void createMddForPackageDenyRegistry() throws Exception {
        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getDenyPackageManifestPopulator(
                        mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains deny package FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    private long getNumEntriesInEncryptionKeysTable() {
        return DatabaseUtils.queryNumEntries(
                mDbHelper.getReadableDatabase(),
                EncryptionKeyTables.EncryptionKeyContract.TABLE,
                null);
    }

    private long getNumEntriesInEnrollmentTable() {
        return DatabaseUtils.queryNumEntries(
                mDbHelper.getReadableDatabase(),
                EnrollmentTables.EnrollmentDataContract.TABLE,
                null);
    }

    private void verifyEncryptionKeysFileGroup(
            ClientFileGroup clientFileGroup, int numberOfExpectedKeys)
            throws InterruptedException, ExecutionException {
        expect.that(clientFileGroup.getGroupName()).isEqualTo(ENCRYPTION_KEYS_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        // Number of enrollment ids with provided encryption keys.
        expect.that(clientFileGroup.getFileCount()).isEqualTo(numberOfExpectedKeys);
        expect.that(
                        clientFileGroup.getFileList().stream()
                                .map(ClientConfigProto.ClientFile::getFileId)
                                .collect(Collectors.toList()))
                .containsExactly("E4.json", "ptb.json");
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);

        doReturn(mMdd).when(() -> MobileDataDownloadFactory.getMdd(any(Flags.class)));

        EncryptionKeyDao encryptionKeyDao = new EncryptionKeyDao(mDbHelper);
        EncryptionDataDownloadManager encryptionDataDownloadManager =
                new EncryptionDataDownloadManager(mMockFlags, encryptionKeyDao, mMockClock);

        // Verify encryption keys data file read from MDD and insert the data into the encryption
        // keys database.
        expect.that(encryptionDataDownloadManager.readAndInsertEncryptionDataFromMdd().get())
                .isEqualTo(EncryptionDataDownloadManager.DownloadStatus.SUCCESS);
        expect.that(getNumEntriesInEncryptionKeysTable()).isEqualTo(numberOfExpectedKeys);
    }

    private void verifyEnrollmentMddDownloadStatus(
            ClientFileGroup clientFileGroup, int fileGroupVersion) {
        expect.that(clientFileGroup.getGroupName()).isEqualTo(ENROLLMENT_FILE_GROUP_NAME);
        expect.that(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        expect.that(clientFileGroup.getFileCount()).isEqualTo(1);
        expect.that(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        expect.that(clientFileGroup.getVersionNumber()).isEqualTo(fileGroupVersion);
    }

    private EnrollmentDao setupEnrollmentDaoForTest() {
        EnrollmentDao enrollmentDao = new EnrollmentDao(mContext, mDbHelper, mMockFlags);
        doReturn(enrollmentDao).when(EnrollmentDao::getInstance);
        return enrollmentDao;
    }

    private EnrollmentDataDownloadManager setupEnrollmentDownloadManagerForTest() {
        EnrollmentDataDownloadManager enrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(mContext, mMockFlags);

        EncryptionKeyDao encryptionKeyDao = new EncryptionKeyDao(mDbHelper);
        doReturn(encryptionKeyDao).when(EncryptionKeyDao::getInstance);
        return enrollmentDataDownloadManager;
    }

    private void verifyMeasurementFileGroup(
            ClientFileGroup clientFileGroup, int fileGroupVersion, int enrollmentEntries)
            throws InterruptedException, ExecutionException {
        verifyMeasurementFileGroup(
                clientFileGroup,
                fileGroupVersion,
                enrollmentEntries,
                /* clearExistingData= */ true,
                /* clearDownloadedData= */ true);
    }

    private void verifyMeasurementFileGroup(
            ClientFileGroup clientFileGroup,
            int fileGroupVersion,
            int enrollmentEntries,
            boolean clearExistingData,
            boolean clearDownloadedData)
            throws InterruptedException, ExecutionException {
        verifyEnrollmentMddDownloadStatus(clientFileGroup, fileGroupVersion);

        doReturn(mMdd).when(() -> MobileDataDownloadFactory.getMdd(any(Flags.class)));

        EnrollmentDao enrollmentDao = setupEnrollmentDaoForTest();
        if (clearExistingData) {
            assertThat(enrollmentDao.deleteAll()).isTrue();
            // Verify no enrollment data after table cleared.
            assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(0);
        }

        EnrollmentDataDownloadManager enrollmentDataDownloadManager =
                setupEnrollmentDownloadManagerForTest();
        // Verify enrollment data file read from MDD and insert the data into the enrollment
        // database.
        expect.that(enrollmentDataDownloadManager.readAndInsertEnrollmentDataFromMdd().get())
                .isEqualTo(SUCCESS);
        expect.that(getNumEntriesInEnrollmentTable()).isEqualTo(enrollmentEntries);
        if (clearDownloadedData) {
            expect.that(enrollmentDao.deleteAll()).isTrue();
        }
    }

    private void mockMddFlags() {
        mocker.mockGetFlags(mMockFlags);

        doReturn(2).when(mMockFlags).getDownloaderMaxDownloadThreads();
        doReturn(false).when(mMockFlags).getEncryptionKeyNewEnrollmentFetchKillSwitch();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkConnectTimeoutMs();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkReadTimeoutMs();
    }

    private static void overridingMddLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.MDD %s", loggingLevel);
    }
}
