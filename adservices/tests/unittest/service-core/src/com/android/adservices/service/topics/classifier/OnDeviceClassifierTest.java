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

package com.android.adservices.service.topics.classifier;

import static com.android.adservices.service.Flags.CLASSIFIER_NUMBER_OF_TOP_LABELS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationClassifierStats;
import com.android.adservices.service.topics.AppInfo;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.PackageManagerUtil;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** Topic Classifier Test {@link OnDeviceClassifier}. */
public class OnDeviceClassifierTest {
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_PRECOMPUTED_FILE_PATH =
            "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String TEST_CLASSIFIER_MODEL_PATH = "classifier/test_model.tflite";

    // (b/244313803): Refactor tests to remove DeviceConfig and use Flags.
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static Preprocessor sPreprocessor;

    @Mock private PackageManagerUtil mPackageManagerUtil;
    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock private ModelManager mModelManager;
    @Mock private CacheManager mCacheManager;
    @Mock Map<String, ClientFile> mMockDownloadedFiles;
    private OnDeviceClassifier mOnDeviceClassifier;
    @Mock AdServicesLogger mLogger;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_PRECOMPUTED_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        sPreprocessor = new Preprocessor(sContext);
        mOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor,
                        mPackageManagerUtil,
                        new Random(),
                        mModelManager,
                        mCacheManager,
                        mLogger);
        when(mCacheManager.getTopicsWithRevokedConsent()).thenReturn(ImmutableList.of());
    }

    @Test
    public void testClassify_earlyReturnIfNoModelAvailable() {
        mModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_PRECOMPUTED_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_PATH,
                        "ModelWrongPath",
                        mMockFileStorage,
                        null /*No downloaded files.*/);
        sPreprocessor = new Preprocessor(sContext);
        mOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor,
                        mPackageManagerUtil,
                        new Random(),
                        mModelManager,
                        mCacheManager,
                        mLogger);

        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        // Result is empty due to no bundled model available.
        assertThat(classifications).isEmpty();
    }

    @Test
    public void testClassify_packageManagerError_returnsDefaultClassifications() {
        ArgumentCaptor<EpochComputationClassifierStats> argument =
                ArgumentCaptor.forClass(EpochComputationClassifierStats.class);
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // If fetch from PackageManagerUtil fails, we will use empty strings as descriptions.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(ImmutableMap.of());

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(1);
        // Verify default classification.
        assertThat(classifications.get(appPackage1)).hasSize(CLASSIFIER_NUMBER_OF_TOP_LABELS);
        // Check all the returned labels for default empty string descriptions.
        assertThat(classifications.get(appPackage1))
                .isEqualTo(createRealTopics(Arrays.asList(10230, 10253, 10227)));

        // Verify logged atom.
        verify(mLogger).logEpochComputationClassifierStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of(10230, 10253, 10227))
                                .setBuildId(8)
                                .setAssetVersion("2")
                                .setClassifierType(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                                .setPrecomputedClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                                .build());
    }

    @Test
    public void testClassify_verifyClassifierDescriptionFlags() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        // One value for input package name.
        assertThat(classifications).hasSize(1);
        // Verify size of the labels returned.
        assertThat(classifications.get(appPackage1)).hasSize(3);

        // Check if the first category matches in the top CLASSIFIER_NUMBER_OF_TOP_LABELS.
        // Scores can currently differ when running tests on-server vs. on-device, as server
        // tests use original Tensorflow model while device tests use exported TFLite model.
        // Until this is changed, to ensure consistent test output across devices we only check the
        // first top expected topic, which always scores high enough to be returned by both models.
        // TODO (after b/264446621): Check all topics once server-side tests also use TFLite model.
        // Expected top 10: 10253, 10230, 10284, 10237, 10227, 10257, 10165, 10028, 10330, 10047
        assertThat(classifications.get(appPackage1))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10253)));

        // Set max classifier description length to 0. This should make description an empty string.
        setClassifierDescriptionLength(0);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        // Run classification again for the same package.
        classifications = mOnDeviceClassifier.classify(appPackages);

        // Verify values are the same values as returned for an empty string.
        assertThat(classifications.get(appPackage1))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10230, 10253, 10227)));
    }

    @Test
    public void testClassify_successfulClassifications() {
        ArgumentCaptor<EpochComputationClassifierStats> argument =
                ArgumentCaptor.forClass(EpochComputationClassifierStats.class);
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .put(
                                appPackage2,
                                new AppInfo(
                                        "appName2",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        // Two values for two input package names.
        assertThat(classifications).hasSize(2);
        // Verify size of the labels returned.
        assertThat(classifications.get(appPackage1)).hasSize(3);
        assertThat(classifications.get(appPackage2)).hasSize(CLASSIFIER_NUMBER_OF_TOP_LABELS);

        // Check if the first category matches in the top CLASSIFIER_NUMBER_OF_TOP_LABELS.
        // Scores can currently differ when running tests on-server vs. on-device, as server
        // tests use original Tensorflow model while device tests use exported TFLite model.
        // Until this is changed, to ensure consistent test output across devices we only check the
        // first top expected topic, which always scores high enough to be returned by both models.
        // TODO (after b/264446621): Check all topics once server-side tests also use TFLite model.
        // Expected top 10: 10253, 10230, 10284, 10237, 10227, 10257, 10165, 10028, 10330, 10047
        assertThat(classifications.get(appPackage1))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10253)));
        // Expected top 10: 10227, 10225, 10235, 10230, 10238, 10253, 10247, 10254, 10234, 10229
        assertThat(classifications.get(appPackage2))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10227)));

        // Verify logged atom.
        verify(mLogger, times(2)).logEpochComputationClassifierStats(argument.capture());
        assertThat(argument.getAllValues()).hasSize(2);
        // Log for appPackage1.
        assertThat(argument.getAllValues().get(0))
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of(10253, 10230, 10237))
                                .setBuildId(8)
                                .setAssetVersion("2")
                                .setClassifierType(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                                .setPrecomputedClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                                .build());
        // Log for appPackage2.
        assertThat(argument.getAllValues().get(1))
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of(10230, 10227, 10238))
                                .setBuildId(8)
                                .setAssetVersion("2")
                                .setClassifierType(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                                .setPrecomputedClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                                .build());
    }

    @Test
    public void testClassify_appWithBlockedTopic() {
        // The expected classification will include this topic before it is blocked.
        int blockedTopicId = 10253;

        // Set up description for sample app.
        String appPackage = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.of(appPackage, new AppInfo("appName", "Sample app description."));
        ImmutableSet<String> appPackages = appInfoMap.keySet();
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        // Check that the topic is returned before blocking it.
        List<Integer> topTopicIdsBeforeBlocking =
                mOnDeviceClassifier.classify(appPackages).get(appPackage).stream()
                        .map(Topic::getTopic)
                        .collect(Collectors.toList());
        assertThat(topTopicIdsBeforeBlocking).contains(blockedTopicId);

        // Block the topic.
        when(mCacheManager.getTopicsWithRevokedConsent())
                .thenReturn(ImmutableList.of(createDummyTopic(blockedTopicId)));

        // Reload classifier to refresh blocked topics.
        mOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor,
                        mPackageManagerUtil,
                        new Random(),
                        mModelManager,
                        mCacheManager,
                        mLogger);

        // Check that the topic is not returned after blocking it.
        List<Integer> topTopicIdsAfterBlocking =
                mOnDeviceClassifier.classify(appPackages).get(appPackage).stream()
                        .map(Topic::getTopic)
                        .collect(Collectors.toList());
        assertThat(topTopicIdsAfterBlocking).doesNotContain(blockedTopicId);
    }

    @Test
    public void testClassify_appWithAllTopicsBlocked() {
        // The expected classification will include these three topics before they are blocked.
        List<Integer> blockedTopicIds = ImmutableList.of(10253, 10230, 10237);

        // Set up description for sample app.
        String appPackage = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.of(appPackage, new AppInfo("appName", "Sample app description."));
        ImmutableSet<String> appPackages = appInfoMap.keySet();
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        // Check that all the expected topics are returned before blocking.
        List<Integer> topTopicIdsBeforeBlocking =
                mOnDeviceClassifier.classify(appPackages).get(appPackage).stream()
                        .map(Topic::getTopic)
                        .collect(Collectors.toList());
        assertThat(topTopicIdsBeforeBlocking).containsExactlyElementsIn(blockedTopicIds);

        // Block all 10 topics.
        when(mCacheManager.getTopicsWithRevokedConsent())
                .thenReturn(ImmutableList.copyOf(createDummyTopics(blockedTopicIds)));

        // Reload classifier to refresh blocked topics.
        mOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor,
                        mPackageManagerUtil,
                        new Random(),
                        mModelManager,
                        mCacheManager,
                        mLogger);

        // The correct response should contain no topics.
        List<Topic> topTopicsAfterBlocking =
                mOnDeviceClassifier.classify(appPackages).get(appPackage);
        assertThat(topTopicsAfterBlocking).isEmpty();
    }

    @Test
    public void testClassify_successfulClassifications_overrideNumberOfTopLabels() {
        ArgumentCaptor<EpochComputationClassifierStats> argument =
                ArgumentCaptor.forClass(EpochComputationClassifierStats.class);
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);
        // Override classifierNumberOfTopLabels.
        int overrideNumberOfTopLabels = 0;
        setClassifierNumberOfTopLabels(overrideNumberOfTopLabels);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(1);
        // Verify size of the labels returned is equal to the override value.
        assertThat(classifications.get(appPackage1)).hasSize(overrideNumberOfTopLabels);
        // Verify logged atom.
        verify(mLogger).logEpochComputationClassifierStats(argument.capture());
        assertThat(argument.getValue())
                .isEqualTo(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.of())
                                .setBuildId(8)
                                .setAssetVersion("2")
                                .setClassifierType(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE)
                                .setPrecomputedClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                                .build());
    }

    @Test
    public void testClassify_successfulClassifications_overrideClassifierThreshold() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);
        // Override classifierThreshold.
        float overrideThreshold = 0.1f;
        setClassifierThreshold(overrideThreshold);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(1);
        // Expecting 2 values greater than 0.1 threshold.
        assertThat(classifications.get(appPackage1)).hasSize(3);
    }

    @Test
    public void testClassify_successfulClassificationsForUpdatedAppDescription() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> oldAppInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableMap<String, AppInfo> newAppInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(
                                appPackage1,
                                new AppInfo(
                                        "appName1",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // Return old description first and then the new description.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages)))
                .thenReturn(oldAppInfoMap)
                .thenReturn(newAppInfoMap);

        ImmutableMap<String, List<Topic>> firstClassifications =
                mOnDeviceClassifier.classify(appPackages);
        ImmutableMap<String, List<Topic>> secondClassifications =
                mOnDeviceClassifier.classify(appPackages);

        // Verify two calls to packageManagerUtil.
        verify(mPackageManagerUtil, times(2)).getAppInformation(eq(appPackages));
        // Two values for two input package names.
        assertThat(secondClassifications).hasSize(1);
        // Verify size of the labels returned is CLASSIFIER_NUMBER_OF_TOP_LABELS.
        assertThat(secondClassifications.get(appPackage1)).hasSize(CLASSIFIER_NUMBER_OF_TOP_LABELS);

        // Check if the first category matches in the top CLASSIFIER_NUMBER_OF_TOP_LABELS.
        // Scores can currently differ when running tests on-server vs. on-device, as server
        // tests use original Tensorflow model while device tests use exported TFLite model.
        // Until this is changed, to ensure consistent test output across devices we only check the
        // first top expected topic, which always scores high enough to be returned by both models.
        // TODO (after b/264446621): Check all topics once server-side tests also use TFLite model.
        // Check different expected scores for different descriptions.
        // Expected top 10: 10253, 10230, 10284, 10237, 10227, 10257, 10165, 10028, 10330, 10047
        assertThat(firstClassifications.get(appPackage1))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10253)));
        // Expected top 10: 10227, 10225, 10235, 10230, 10238, 10253, 10247, 10254, 10234, 10229
        assertThat(secondClassifications.get(appPackage1))
                .containsAtLeastElementsIn(createRealTopics(Arrays.asList(10227)));
    }

    @Test
    public void testClassify_emptyInput_emptyOutput() {
        assertThat(mOnDeviceClassifier.classify(ImmutableSet.of())).isEmpty();
    }

    @Test
    public void testGetTopTopics_fetchTopAndRandomTopics() {
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        String appPackage3 = "com.example.adservices.samples.topics.sampleapp3";
        String commonAppDescription =
                "This xyz game is the best adventure game to thrill"
                        + " our users! Play, win and share with your"
                        + " friends to win more coins.";
        int numberOfTopTopics = 4, numberOfRandomTopics = 1;
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2, appPackage3);
        // Two packages have same description.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages)))
                .thenReturn(
                        ImmutableMap.<String, AppInfo>builder()
                                .put(
                                        appPackage1,
                                        new AppInfo("appName1", "Sample app description."))
                                .put(appPackage2, new AppInfo("appName2", commonAppDescription))
                                .put(appPackage3, new AppInfo("appName3", commonAppDescription))
                                .build());

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);
        List<Topic> topTopics =
                mOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(3);
        // Check if the returned list has numberOfTopTopics topics.
        assertThat(topTopics).hasSize(numberOfTopTopics + numberOfRandomTopics);
        // Verify the top topics are from the description that was repeated.
        List<Topic> expectedLabelsForCommonDescription =
                createRealTopics(Arrays.asList(10230, 10227, 10238, 10253));
        assertThat(topTopics.subList(0, numberOfTopTopics))
                .containsAnyIn(expectedLabelsForCommonDescription);
    }

    @Test
    public void testGetTopTopics_verifyRandomTopics() {
        // Verify the last 4 random topics are not the same.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .put(
                                appPackage2,
                                new AppInfo(
                                        "appName2",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        int numberOfTopTopics = 1, numberOfRandomTopics = 4;
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);
        List<Topic> topTopics1 =
                mOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);
        List<Topic> topTopics2 =
                mOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(2);
        // Verify random topics are not the same.
        assertThat(topTopics1.subList(numberOfTopTopics, numberOfTopTopics + numberOfRandomTopics))
                .isNotEqualTo(
                        topTopics2.subList(
                                numberOfTopTopics, numberOfTopTopics + numberOfRandomTopics));
    }

    @Test
    public void testBertModelVersion_matchesAssetsModelVersion() {
        assertThat(mOnDeviceClassifier.getBertModelVersion())
                .isEqualTo(mOnDeviceClassifier.getModelVersion());
    }

    @Test
    public void testBertLabelsVersion_matchesAssetsLabelsVersion() {
        assertThat(mOnDeviceClassifier.getBertLabelsVersion())
                .isEqualTo(mOnDeviceClassifier.getLabelsVersion());
    }

    // Creates a dummy topic.  Not suitable for tests where
    // label/model version are verified, but suitable for mocks
    // where classifier methods cannot first be called.
    private static Topic createDummyTopic(int topicId) {
        return Topic.create(topicId, 0, 0);
    }

    private static List<Topic> createDummyTopics(List<Integer> topicIds) {
        return topicIds.stream()
                .map(OnDeviceClassifierTest::createDummyTopic)
                .collect(Collectors.toList());
    }

    private Topic createRealTopic(int topicId) {
        return Topic.create(
                topicId,
                mOnDeviceClassifier.getLabelsVersion(),
                mOnDeviceClassifier.getModelVersion());
    }

    private List<Topic> createRealTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::createRealTopic).collect(Collectors.toList());
    }

    private void setClassifierNumberOfTopLabels(int overrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "classifier_number_of_top_labels",
                Integer.toString(overrideValue),
                /* makeDefault */ false);
    }

    private void setClassifierThreshold(float overrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "classifier_threshold",
                Float.toString(overrideValue),
                /* makeDefault */ false);
    }

    private void setClassifierDescriptionLength(int overrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "classifier_description_max_length",
                Integer.toString(overrideValue),
                /* makeDefault */ false);
    }
}
