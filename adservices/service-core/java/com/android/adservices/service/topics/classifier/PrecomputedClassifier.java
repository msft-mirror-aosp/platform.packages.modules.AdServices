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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS;

import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.EpochComputationClassifierStats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Precomputed Classifier.
 *
 * <p>This Classifier will classify app into list of Topics using the server side classifier. The
 * classification results for the top K apps are computed on the server and stored on the device.
 *
 * <p>This class is not thread safe.
 */
@NotThreadSafe
public class PrecomputedClassifier implements Classifier {

    private static PrecomputedClassifier sSingleton;

    private static final String MODEL_ASSET_FIELD = "tflite_model";
    private static final String LABELS_ASSET_FIELD = "labels_topics";
    private static final String ASSET_VERSION_FIELD = "asset_version";
    private static final String VERSION_INFO_FIELD = "version_info";
    private static final String BUILD_ID_FIELD = "build_id";

    private final ModelManager mModelManager;

    // Used to mark whether the assets are loaded
    private boolean mLoaded;
    private ImmutableList<Integer> mLabels;
    // The app topics map Map<App, List<Topic>>
    private Map<String, List<Integer>> mAppTopics = new HashMap<>();
    private long mModelVersion;
    private long mLabelsVersion;
    private int mBuildId;
    private final AdServicesLogger mLogger;

    PrecomputedClassifier(@NonNull ModelManager modelManager, @NonNull AdServicesLogger logger) {
        mModelManager = modelManager;
        mLoaded = false;
        mLogger = logger;
    }

    @NonNull
    @Override
    public Map<String, List<Topic>> classify(@NonNull Set<String> apps) {
        if (!isLoaded()) {
            load();
        }

        Map<String, List<Topic>> appsToClassifiedTopics = new HashMap<>(apps.size());

        for (String app : apps) {
            if (app != null && !app.isEmpty()) {
                List<Integer> topicIds = mAppTopics.getOrDefault(app, ImmutableList.of());
                List<Topic> topics =
                        topicIds.stream().map(this::createTopic).collect(Collectors.toList());

                // Log atom for getTopTopics call.
                mLogger.logEpochComputationClassifierStats(
                        EpochComputationClassifierStats.builder()
                                .setTopicIds(ImmutableList.copyOf(topicIds))
                                .setBuildId(mBuildId)
                                .setAssetVersion(Long.toString(mModelVersion))
                                .setClassifierType(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER)
                                .setOnDeviceClassifierStatus(
                                        AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED)
                                .setPrecomputedClassifierStatus(
                                        topicIds.isEmpty()
                                                ? AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE
                                                : AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS)
                                .build());

                appsToClassifiedTopics.put(app, topics);
            }
        }
        return appsToClassifiedTopics;
    }

    @NonNull
    @Override
    public List<Topic> getTopTopics(
            @NonNull Map<String, List<Topic>> appTopics,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics) {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return CommonClassifierHelper.getTopTopics(
                appTopics, mLabels, new Random(), numberOfTopTopics, numberOfRandomTopics, mLogger);
    }

    long getModelVersion() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mModelVersion;
    }

    long getLabelsVersion() {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return mLabelsVersion;
    }

    private Topic createTopic(int topicId) {
        return Topic.create(topicId, mLabelsVersion, mModelVersion);
    }

    // Load labels and app topics.
    private void load() {
        mLabels = mModelManager.retrieveLabels();
        mAppTopics = mModelManager.retrieveAppClassificationTopics();

        // Load classifier assets metadata.
        ImmutableMap<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                mModelManager.retrieveClassifierAssetsMetadata();
        mModelVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(MODEL_ASSET_FIELD).get(ASSET_VERSION_FIELD));
        mLabelsVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(LABELS_ASSET_FIELD).get(ASSET_VERSION_FIELD));
        try {
            mBuildId =
                    Integer.parseInt(
                            classifierAssetsMetadata.get(VERSION_INFO_FIELD).get(BUILD_ID_FIELD));
        } catch (NumberFormatException e) {
            // No build id is available.
            LogUtil.d(e, "Build id is not available");
            mBuildId = -1;
        }
        mLoaded = true;
    }

    // Indicates whether labels and app topics are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }
}
