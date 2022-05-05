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
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Topics Classifier.
 *
 * This Classifier will classify app into list of Topics.
 *
 * This class is not thread safe.
 */

@NotThreadSafe
public class Classifier {

    private static Classifier sSingleton;
    private static final String LABELS_FILE_PATH =
            "classifier/labels_chrome_topics.txt";
    private static final String TOP_APP_FILE_PATH =
            "classifier/precomputed_app_list_chrome_topics.csv";

    private final PrecomputedLoader mPrecomputedLoader;
    private final Random mRandom;

    // Used to mark whether the assets are loaded
    private boolean mLoaded;
    private ImmutableSet<String> mLabels;
    // The app topics map Map<App, List<Topic>>
    private Map<String, List<String>> mAppTopics = new HashMap<>();

    private Classifier(
            @NonNull PrecomputedLoader precomputedLoader,
            @NonNull Random random) throws IOException {
        mPrecomputedLoader = precomputedLoader;
        mRandom = random;
        mLoaded = false;
    }

    /** Returns an instance of the Classifier given a context. */
    @NonNull
    public static Classifier getInstance(@NonNull Context context) {
        synchronized (Classifier.class) {
            if (sSingleton == null) {
                try {
                    sSingleton = new Classifier(new PrecomputedLoader(
                            context, LABELS_FILE_PATH, TOP_APP_FILE_PATH), new Random());
                } catch (IOException e) {
                    LogUtil.e(e, "Unable to read precomputed labels and app topics list");
                }
            }
        }
        return sSingleton;
    }

    /**
     * This method will return a map from the app to the list of its classification topics.
     * If an app is not found in the db, an empty list of topic will be assigned for that app.
     *
     * @param apps The set of apps
     * @return {@code appClassificationTopicsMap = Map<App, List<Topic>>}
     */
    @NonNull
    public Map<String, List<String>> classify(@NonNull Set<String> apps) {
        if (!isLoaded()) {
            load();
        }

        Map<String, List<String>> appsToTopicsClassification = new HashMap<>();

        for (String app : apps) {
            if (app != null && app.length() > 0) {
                appsToTopicsClassification.put(app,
                        mAppTopics.getOrDefault(app, new ArrayList<>()));
            }
        }
        return appsToTopicsClassification;
    }

    /**
     * This method will generate numberOfTopTopics of top topics followed by numberOfRandomTopics
     * random topics.
     *
     * In the case we don't have enough topics to generate numberOfTopTopics of top topics, we
     * will pad them with random topics.
     *
     * The result of this function is a list of numberOfTopTopics + numberOfRandomTopics topics.
     *
     * @param appTopics A hashmap that save the user's app and their topics
     * @param numberOfTopTopics The number of top Topics to be returned
     * @param numberOfRandomTopics The number of top Topics to be returned
     * @return A list of topics where Top Topics precede the random topics.
     */
    @NonNull
    public List<String> getTopTopics(
            @NonNull Map<String, List<String>> appTopics,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics) {
        Preconditions.checkArgument(numberOfTopTopics > 0,
                "numberOfTopTopics should larger than 0");
        Preconditions.checkArgument(numberOfRandomTopics > 0,
                "numberOfRandomTopics should larger than 0");

        if (!isLoaded()) {
            load();
        }

        // A map from Topics to the count of its occurrences
        Map<String, Integer> topicsToAppTopicCount = new HashMap<>();
        for (List<String> appTopic : appTopics.values()) {
            for (String topic : appTopic) {
                topicsToAppTopicCount.put(
                        topic, topicsToAppTopicCount.getOrDefault(topic, 0) + 1);
            }
        }

        // Sort the topics by their count
        List<String> allSortedTopics = topicsToAppTopicCount.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // The number of topics to pad in top topics
        int numberOfRandomPaddingTopics = Math.max(0, numberOfTopTopics - allSortedTopics.size());
        List<String> topTopics = allSortedTopics.subList(
                0, Math.min(numberOfTopTopics, allSortedTopics.size()));

        // If the size of topTopics smaller than numberOfTopTopics,
        // the top topics list will be padded by numberOfRandomPaddingTopics random topics.
        return getRandomTopics(topTopics,
                numberOfRandomTopics + numberOfRandomPaddingTopics);
    }

    /**
     * This helper function will populate numOfRandomTopics random topics in the topTopics list
     *
     * @param topTopics List of top topics got from hardcoded csv table
     * @param numberOfRandomTopics number of random topics need to be populated
     * @return A list of topics where top topics precede numOfRandomTopics random topics.
     */
    @NonNull
    private List<String> getRandomTopics(
            @NonNull List<String> topTopics,
            @NonNull int numberOfRandomTopics) {
        if (numberOfRandomTopics <= 0) {
            return topTopics;
        }

        List<String> returnedTopics = new ArrayList<>();

        // First add all the top topics
        returnedTopics.addAll(topTopics);

        // Counter of how many random topics need to add
        int topicsCounter = numberOfRandomTopics;

        // Then add random topics
        while (topicsCounter > 0 && returnedTopics.size() < mLabels.size()) {
            // TODO(b/226457861): unit test for this random logic
            int randInt = mRandom.nextInt(mLabels.size());
            // mLabels is an immutable set,
            // it should be converted to array before picking up one element randomly
            String randTopic = mLabels.toArray()[randInt].toString();
            if (returnedTopics.contains(randTopic)) {
                continue;
            }

            returnedTopics.add(randTopic);
            topicsCounter--;
        }

        return returnedTopics;
    }

    /**
     * Load labels and app topics.
     */
    private void load() {
        mLabels = mPrecomputedLoader.retrieveLabels();
        mAppTopics = mPrecomputedLoader.retrieveAppClassificationTopics();
        mLoaded = true;
    }

    /**
     * Indicates whether labels and app topics are loaded.
     *
     * @return whether assets are loaded.
     */
    private boolean isLoaded() {
        return mLoaded;
    }
}
