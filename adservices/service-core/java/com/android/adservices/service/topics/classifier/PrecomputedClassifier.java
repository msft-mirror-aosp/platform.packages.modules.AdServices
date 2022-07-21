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

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
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
 * This Classifier will classify app into list of Topics using the server side classifier. The
 * classification results for the top K apps are computed on the server and stored on the device.
 *
 * This class is not thread safe.
 */

@NotThreadSafe
public class PrecomputedClassifier implements Classifier {

    private static PrecomputedClassifier sSingleton;

    private static final String LABELS_FILE_PATH =
            "classifier/labels_topics.txt";
    private static final String TOP_APP_FILE_PATH =
            "classifier/precomputed_app_list.csv";
    private static final String CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_assets_metadata.json";

    private static final String MODEL_ASSET_FIELD = "tflite_model";
    private static final String LABELS_ASSET_FIELD = "labels_topics";
    private static final String ASSET_VERSION_FIELD = "asset_version";

    private final PrecomputedLoader mPrecomputedLoader;

    // Used to mark whether the assets are loaded
    private boolean mLoaded;
    private ImmutableList<Integer> mLabels;
    // The app topics map Map<App, List<Topic>>
    private Map<String, List<Integer>> mAppTopics = new HashMap<>();
    private long mModelVersion;
    private long mLabelsVersion;

    PrecomputedClassifier(@NonNull PrecomputedLoader precomputedLoader) throws IOException {
        mPrecomputedLoader = precomputedLoader;
        mLoaded = false;
    }

    /** Returns an instance of the PrecomputedClassifier given a context. */
    @NonNull
    public static PrecomputedClassifier getInstance(@NonNull Context context) {
        synchronized (PrecomputedClassifier.class) {
            if (sSingleton == null) {
                try {
                    PrecomputedLoader precomputedLoader =
                            new PrecomputedLoader(
                                    context,
                                    LABELS_FILE_PATH,
                                    TOP_APP_FILE_PATH,
                                    CLASSIFIER_ASSETS_METADATA_PATH);
                    sSingleton = new PrecomputedClassifier(precomputedLoader);
                } catch (IOException e) {
                    LogUtil.e(e, "Unable to read precomputed labels and app topics list");
                }
            }
        }
        return sSingleton;
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
                appTopics, mLabels, new Random(), numberOfTopTopics, numberOfRandomTopics);
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
        mLabels = mPrecomputedLoader.retrieveLabels();
        mAppTopics = mPrecomputedLoader.retrieveAppClassificationTopics();

        // Load classifier assets metadata.
        ImmutableMap<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                mPrecomputedLoader.retrieveClassifierAssetsMetadata();
        mModelVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(MODEL_ASSET_FIELD).get(ASSET_VERSION_FIELD));
        mLabelsVersion =
                Long.parseLong(
                        classifierAssetsMetadata.get(LABELS_ASSET_FIELD).get(ASSET_VERSION_FIELD));

        mLoaded = true;
    }

    // Indicates whether labels and app topics are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }
}
