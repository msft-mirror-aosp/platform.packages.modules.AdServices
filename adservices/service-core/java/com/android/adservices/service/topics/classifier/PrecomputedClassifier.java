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

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
            "classifier/labels_chrome_topics.txt";
    private static final String TOP_APP_FILE_PATH =
            "classifier/precomputed_app_list_chrome_topics.csv";

    private final PrecomputedLoader mPrecomputedLoader;

    // Used to mark whether the assets are loaded
    private boolean mLoaded;
    private ImmutableList<Integer> mLabels;
    // The app topics map Map<App, List<Topic>>
    private Map<String, List<Integer>> mAppTopics = new HashMap<>();

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
                            new PrecomputedLoader(context, LABELS_FILE_PATH, TOP_APP_FILE_PATH);
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
    public Map<String, List<Integer>> classify(@NonNull Set<String> apps) {
        if (!isLoaded()) {
            load();
        }

        Map<String, List<Integer>> appsToTopicsClassification = new HashMap<>();

        for (String app : apps) {
            if (app != null && app.length() > 0) {
                appsToTopicsClassification.put(app,
                        mAppTopics.getOrDefault(app, ImmutableList.of()));
            }
        }
        return appsToTopicsClassification;
    }

    @NonNull
    @Override
    public List<Integer> getTopTopics(
            @NonNull Map<String, List<Integer>> appTopics,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics) {
        // Load assets if not loaded already.
        if (!isLoaded()) {
            load();
        }

        return CommonClassifierHelper.getTopTopics(
                appTopics, mLabels, new Random(), numberOfTopTopics, numberOfRandomTopics);
    }

    // Load labels and app topics.
    private void load() {
        mLabels = mPrecomputedLoader.retrieveLabels();
        mAppTopics = mPrecomputedLoader.retrieveAppClassificationTopics();
        mLoaded = true;
    }

    // Indicates whether labels and app topics are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }
}
