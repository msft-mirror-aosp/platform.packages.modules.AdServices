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
import android.util.JsonReader;

import com.android.adservices.LogUtil;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/** Helper methods for shared implementations of {@link Classifier}. */
class CommonClassifierHelper {
    // The key name of asset metadata property in classifier_assets_metadata.json
    private static final String ASSET_PROPERTY_NAME = "property";
    // The key name of asset element in classifier_assets_metadata.json
    private static final String ASSET_ELEMENT_NAME = "asset_name";
    // The attributions of assets property in classifier_assets_metadata.json
    private static final Set<String> ASSETS_PROPERTY_ATTRIBUTIONS = new HashSet(
            Arrays.asList("taxonomy_type", "taxonomy_version", "updated_date"));
    // The attributions of assets metadata in classifier_assets_metadata.json
    private static final Set<String> ASSETS_NORMAL_ATTRIBUTIONS = new HashSet(
            Arrays.asList("asset_version", "path", "checksum", "updated_date"));
    // The algorithm name of checksum
    private static final String SHA256_DIGEST_ALGORITHM_NAME = "SHA-256";

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
     * Retrieve the assets names and their corresponding metadata.
     *
     * @return The immutable map of assets metadata from {@code classifierAssetsMetadataPath}.
     *      Empty map will be returned for {@link IOException}.
     */
    @NonNull
    static ImmutableMap<String, ImmutableMap<String, String>> getAssetsMetadata(
            @NonNull AssetManager assetManager, @NonNull String classifierAssetsMetadataPath) {
        // Initialize a ImmutableMap.Builder to store the classifier assets metadata iteratively.
        // classifierAssetsMetadata = ImmutableMap<AssetName, ImmutableMap<MetadataName, Value>>
        ImmutableMap.Builder<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                new ImmutableMap.Builder<>();

        try (InputStreamReader inputStreamReader =
                     new InputStreamReader(assetManager.open(classifierAssetsMetadataPath))) {
            JsonReader reader = new JsonReader(inputStreamReader);

            reader.beginArray();
            while (reader.hasNext()) {
                // Use an immutable map to store the metadata of one asset.
                // assetMetadata = ImmutableMap<MetadataName, Value>
                ImmutableMap.Builder<String, String> assetMetadata = new ImmutableMap.Builder<>();

                // Use jsonElementKey to save the key name of each array element.
                String jsonElementKey = null;

                // Begin to read one json element in the array here.
                reader.beginObject();
                if (reader.hasNext()) {
                    String elementKeyName = reader.nextName();

                    if (elementKeyName.equals(ASSET_PROPERTY_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the property's key set.
                            if (ASSETS_PROPERTY_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSETS_PROPERTY_ATTRIBUTIONS.
                                reader.skipValue();
                                LogUtil.e(attribution,
                                        " is a redundant metadata attribution of "
                                                + "metadata property.");
                            }
                        }
                    } else if (elementKeyName.equals(ASSET_ELEMENT_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the asset's key set.
                            if (ASSETS_NORMAL_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSET_NORMAL_ATTRIBUTIONS.
                                reader.skipValue();
                                LogUtil.e(attribution,
                                        " is a redundant metadata attribution of asset.");
                            }
                        }
                    } else {
                        // Skip the json element if it doesn't have key "property" or "asset_name".
                        while (reader.hasNext()) {
                            reader.skipValue();
                        }
                        LogUtil.e("Can't load this json element, "
                                + "because \"property\" or \"asset_name\" "
                                + "can't be found in the json element.");
                    }
                }
                reader.endObject();

                // Save the metadata of the asset if and only if the assetName can be retrieved
                // correctly from the metadata json file.
                if (jsonElementKey != null) {
                    classifierAssetsMetadata.put(jsonElementKey, assetMetadata.build());
                }
            }
            reader.endArray();
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read classifier assets metadata file");
            // When catching IOException -> return empty immutable map
            return ImmutableMap.of();
        }

        return classifierAssetsMetadata.build();
    }

    /**
     * Compute the SHA256 checksum of classifier asset.
     *
     * @return A string of classifier asset's SHA256 checksum.
     */
    static String computeClassifierAssetChecksum(
            @NonNull AssetManager assetManager,
            @NonNull String classifierAssetsMetadataPath) {
        StringBuilder assetSha256CheckSum = new StringBuilder();
        try {
            MessageDigest sha256Digest =
                    MessageDigest.getInstance(SHA256_DIGEST_ALGORITHM_NAME);

            try (InputStream inputStream =
                         assetManager.open(classifierAssetsMetadataPath)) {

                // Create byte array to read data in chunks
                byte[] byteArray = new byte[8192];
                int byteCount = 0;

                // Read file data and update in message digest
                while ((byteCount = inputStream.read(byteArray)) != -1) {
                    sha256Digest.update(byteArray, 0, byteCount);
                }

                // Get the hash's bytes
                byte[] bytes = sha256Digest.digest();

                // This bytes[] has bytes in decimal format;
                // Convert it to hexadecimal format
                for(int i = 0; i < bytes.length; i++) {
                    assetSha256CheckSum.append(
                            Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
                }
            } catch (IOException e) {
                LogUtil.e(e, "Unable to read classifier asset file");
                // When catching IOException -> return empty string.
                return "";
            }
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(e, "Unable to find correct message digest algorithm.");
            // When catching NoSuchAlgorithmException -> return empty string.
            return "";
        }

        return assetSha256CheckSum.toString();
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
