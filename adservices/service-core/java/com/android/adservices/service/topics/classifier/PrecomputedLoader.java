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
import android.content.res.AssetManager;
import android.util.ArrayMap;

import com.android.adservices.LogUtil;

import com.google.common.collect.ImmutableSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PrecomputedLoader to load label file and app topics file which is precomputed server side
 */
public class PrecomputedLoader {
    // Use "\t" as a delimiter to read the precomputed app topics file
    private static final String DELIMITER = "\t";
    // TODO(b/229323531): Implement new encoding method for dynamic topics size
    // Use "None" as a null topic for each app
    private static final String NONE_TOPIC = "None";

    private final AssetManager mAssetManager;
    private final String mLabelsFilePath;
    private final String mTopAppsFilePath;

    public PrecomputedLoader(
            @NonNull Context context,
            @NonNull String labelsFilePath,
            @NonNull String topAppsFilePath) {
        mAssetManager = context.getAssets();
        mLabelsFilePath = labelsFilePath;
        mTopAppsFilePath = topAppsFilePath;
    }

    /**
     * Retrieve a list of topicIDs from labels file.
     *
     * @return The list of topicIDs from labels.txt
     *
     * @throws IOException An empty list will be return
     */
    @NonNull
    public ImmutableSet<Integer> retrieveLabels() {
        // Initialize a ImmutableSet.Builder to store the label iteratively
        ImmutableSet.Builder<Integer> labels = new ImmutableSet.Builder();
        String line;

        try (InputStreamReader inputStreamReader =
                     new InputStreamReader(mAssetManager.open(mLabelsFilePath))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            while ((line = reader.readLine()) != null) {
                labels.add(Integer.parseInt(line));
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed labels");
            // When catching IOException -> return empty immutable set
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableSet.<Integer>builder().build();
        }

        return labels.build();
    }

    /**
     * Retrieve the app classification topicIDs from file name here.
     *
     * @return The map from App to the list of its classification topicIDs.
     *
     * @throws IOException An empty hash map will be return
     */
    @NonNull
    public Map<String, List<Integer>> retrieveAppClassificationTopics() {
        // appTopicsMap = Map<App, List<Topic>>
        Map<String, List<Integer>> appTopicsMap = new ArrayMap<>();
        String line;

        // The immutable set of the topics from labels file
        ImmutableSet<Integer> validTopics = retrieveLabels();

        try (InputStreamReader inputStreamReader =
                     new InputStreamReader(mAssetManager.open(mTopAppsFilePath))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            // Skip first line (columns name)
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(DELIMITER);

                //The first column name is app
                String app = columns[0];

                // This list is used to temporarily store the topicIDs of one app.
                List<Integer> appTopics = new ArrayList<>();

                for (int i = 1; i < columns.length; i++) {
                    String topic = columns[i];
                    // NONE_TOPIC will not be added to the app topics list
                    if (NONE_TOPIC.equals(topic)) {
                        break;
                    }

                    // The topic will not save to the app topics map
                    // if it is not a valid topic in labels file
                    if (!validTopics.contains(Integer.parseInt(topic))) {
                        LogUtil.e("Unable to load topicID \"%s\" in app \"%s\", "
                                + "because it is not a valid topic in labels file.", topic, app);
                        continue;
                    }

                    // The other columns are topics of the app
                    appTopics.add(Integer.parseInt(topic));
                }

                appTopicsMap.put(app, appTopics);
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed app topics list");
            // When catching IOException -> return empty hash map
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return new HashMap<>();
        }

        return appTopicsMap;
    }
}
