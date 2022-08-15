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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.topics.AppInfo;
import com.android.adservices.service.topics.PackageManagerUtil;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Topic Classifier Test {@link OnDeviceClassifier}. */
public class OnDeviceClassifierTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static Preprocessor sPreprocessor;

    @Mock private PackageManagerUtil mPackageManagerUtil;

    private OnDeviceClassifier mOnDeviceClassifier;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        sPreprocessor = new Preprocessor(sContext);
        mOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor,
                        mPackageManagerUtil,
                        new Random(),
                        ModelManager.getInstance(sContext));
    }

    @Test
    public void testGetInstance() {
        OnDeviceClassifier firstInstance = OnDeviceClassifier.getInstance(sContext);
        OnDeviceClassifier secondInstance = OnDeviceClassifier.getInstance(sContext);

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        // Verify singleton behaviour.
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testClassify_packageManagerError_returnsDefaultClassifications() {
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
                .isEqualTo(createTopics(Arrays.asList(
                        10230, 10253, 10227, 10250, 10257, 10225, 10249, 10009, 10223, 10228)));
    }

    @Test
    public void testClassify_successfulClassifications() {
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
        // Verify size of the labels returned is CLASSIFIER_NUMBER_OF_TOP_LABELS.
        assertThat(classifications.get(appPackage1)).hasSize(CLASSIFIER_NUMBER_OF_TOP_LABELS);
        assertThat(classifications.get(appPackage2)).hasSize(CLASSIFIER_NUMBER_OF_TOP_LABELS);

        // Check if the first 10 categories contains at least the top 5.
        // Scores can differ a little on devices. Using this to reduce flakiness.
        // Expected top 10: 10253, 10230, 10284, 10237, 10227, 10257, 10165, 10028, 10330, 10047
        assertThat(classifications.get(appPackage1))
                .containsAtLeastElementsIn(createTopics(Arrays.asList(
                        10237, 10227, 10257, 10165, 10330)));
        // Expected top 10: 10227, 10225, 10235, 10230, 10238, 10253, 10247, 10254, 10234, 10229
        assertThat(classifications.get(appPackage2))
                .containsAtLeastElementsIn(createTopics(
                        Arrays.asList(10227, 10225, 10235, 10230, 10254)));
    }

    @Test
    public void testClassify_successfulClassifications_overrideNumberOfTopLabels() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);
        // Override classifierNumberOfTopLabels.
        int overrideNumberOfTopLabels = 2;
        setClassifierNumberOfTopLabels(overrideNumberOfTopLabels);

        ImmutableMap<String, List<Topic>> classifications =
                mOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(1);
        // Verify size of the labels returned is equal to the override value.
        assertThat(classifications.get(appPackage1)).hasSize(overrideNumberOfTopLabels);
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

        // Check if the first 10 categories contains at least the top 5.
        // Scores can differ a little on devices. Using this to reduce flakiness.
        // Check different expected scores for different descriptions.
        // Expected top 10: 10253, 10230, 10284, 10237, 10227, 10257, 10165, 10028, 10330, 10047
        assertThat(firstClassifications.get(appPackage1))
                .containsAtLeastElementsIn(createTopics(Arrays.asList(
                        10253, 10230, 10284, 10028, 10330)));
        // Expected top 10: 10227, 10225, 10235, 10230, 10238, 10253, 10247, 10254, 10234, 10229
        assertThat(secondClassifications.get(appPackage1))
                .containsAtLeastElementsIn(createTopics(
                        Arrays.asList(10238, 10253, 10247, 10254, 10234)));
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
                createTopics(Arrays.asList(10220, 10235, 10247, 10225));
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

    private Topic createTopic(int topicId) {
        return Topic.create(
                topicId,
                mOnDeviceClassifier.getLabelsVersion(),
                mOnDeviceClassifier.getModelVersion());
    }

    private List<Topic> createTopics(List<Integer> topicIds) {
        return topicIds.stream().map(this::createTopic).collect(Collectors.toList());
    }

    private void setClassifierNumberOfTopLabels(int overrideValue) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "classifier_number_of_top_labels",
                Integer.toString(overrideValue),
                /* makeDefault */ false);
    }
}
