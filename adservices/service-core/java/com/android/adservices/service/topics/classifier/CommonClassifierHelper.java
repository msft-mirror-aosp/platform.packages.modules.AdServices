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
import android.content.res.AssetManager;

import com.android.adservices.LogUtil;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** Helper methods for shared implementations of {@link Classifier}. */
class CommonClassifierHelper {

    /**
     * Retrieve a list of topicIDs from labels file.
     *
     * @return The list of topicIDs from {@code labelsFilePath}. Empty list will be returned for
     *     {@link IOException}.
     */
    @NonNull
    static ImmutableList<Integer> retrieveLabels(
            @NonNull AssetManager assetManager, @NonNull String labelsFilePath) {
        // Initialize a ImmutableList.Builder to store the label ids iteratively.
        ImmutableList.Builder<Integer> labels = new ImmutableList.Builder();
        String line;

        try (InputStreamReader inputStreamReader =
                new InputStreamReader(assetManager.open(labelsFilePath))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            while ((line = reader.readLine()) != null) {
                labels.add(Integer.parseInt(line));
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed labels");
            // When catching IOException -> return empty immutable list
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableList.of();
        }

        return labels.build();
    }

    /**
     * Create a list of top topicIds with numberOfTopTopics + numberOfRandomTopics topicIds.
     *
     * @param appTopics appPackageName to topicIds map.
     * @param labelIds all topicIds from the labels file.
     * @param random to fetch random elements from the labelIds.
     * @param numberOfTopTopics number of top topics to be added at the start of the list.
     * @param numberOfRandomTopics number of random topics to be added at the end of the list.
     * @return a list of topic ids with numberOfTopTopics top predicted topics and
     *     numberOfRandomTopics random topics.
     */
    @NonNull
    static List<Integer> getTopTopics(
            @NonNull Map<String, List<Integer>> appTopics,
            @NonNull List<Integer> labelIds,
            @NonNull Random random,
            @NonNull int numberOfTopTopics,
            @NonNull int numberOfRandomTopics) {
        Preconditions.checkArgument(
                numberOfTopTopics > 0, "numberOfTopTopics should larger than 0");
        Preconditions.checkArgument(
                numberOfRandomTopics > 0, "numberOfRandomTopics should larger than 0");

        // A map from Topics to the count of its occurrences.
        Map<Integer, Integer> topicsToAppTopicCount = new HashMap<>();
        for (List<Integer> appTopic : appTopics.values()) {
            for (Integer topic : appTopic) {
                topicsToAppTopicCount.put(topic, topicsToAppTopicCount.getOrDefault(topic, 0) + 1);
            }
        }

        // If there are no topic in the appTopics list, an empty topic list will be returned.
        if (topicsToAppTopicCount.isEmpty()) {
            LogUtil.w("Unable to retrieve any topics from device.");

            return new ArrayList<>();
        }

        // Sort the topics by their count.
        List<Integer> allSortedTopics =
                topicsToAppTopicCount.entrySet().stream()
                        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        // The number of topics to pad in top topics.
        int numberOfRandomPaddingTopics = Math.max(0, numberOfTopTopics - allSortedTopics.size());
        List<Integer> topTopics =
                allSortedTopics.subList(0, Math.min(numberOfTopTopics, allSortedTopics.size()));

        // If the size of topTopics smaller than numberOfTopTopics,
        // the top topics list will be padded by numberOfRandomPaddingTopics random topics.
        return getRandomTopics(
                labelIds, random, topTopics, numberOfRandomTopics + numberOfRandomPaddingTopics);
    }

    // This helper function will populate numOfRandomTopics random topics in the topTopics list.
    @NonNull
    private static List<Integer> getRandomTopics(
            @NonNull List<Integer> labelIds,
            @NonNull Random random,
            @NonNull List<Integer> topTopics,
            @NonNull int numberOfRandomTopics) {
        if (numberOfRandomTopics <= 0) {
            return topTopics;
        }

        List<Integer> returnedTopics = new ArrayList<>();

        // First add all the topTopics.
        returnedTopics.addAll(topTopics);

        // Counter of how many random topics need to be added.
        int topicsCounter = numberOfRandomTopics;

        // Then add random topics.
        while (topicsCounter > 0 && returnedTopics.size() < labelIds.size()) {
            // Pick up a random topic from labels list and check if it is a duplicate.
            int randTopic = labelIds.get(random.nextInt(labelIds.size()));
            if (returnedTopics.contains(randTopic)) {
                continue;
            }

            returnedTopics.add(randTopic);
            topicsCounter--;
        }

        return returnedTopics;
    }
}
