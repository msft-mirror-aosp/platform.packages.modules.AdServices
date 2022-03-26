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
    private static final String LABELS_FILE_PATH = "labels.txt";
    private static final String TOP_APP_FILE_PATH = "precomputed_app_list.csv";
    // Use "\t" as a delimiter to read the precomputed app topics file
    private static final String DELIMITER = "\t";

    private final AssetManager mAssetManager;

    public PrecomputedLoader(@NonNull Context context) {
        mAssetManager = context.getAssets();
    }

    /**
     * Retrieve a list of string of labels from labels file.
     *
     * @return The list of labels from labels.txt
     *
     * @throws IOException An empty list will be return
     */
    @NonNull
    public List<String> retrieveLabels() {
        List<String> labels = new ArrayList<>();
        String line;

        try (InputStreamReader inputStreamReader =
                     new InputStreamReader(mAssetManager.open(LABELS_FILE_PATH))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read precomputed labels");
            // When catching IOException -> return empty array list
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return new ArrayList<>();
        }

        return labels;
    }

    /**
     * Retrieve the app classification topics from file name here.
     *
     * @return The map from App to the list of its classification topics.
     *
     * @throws IOException An empty hash map will be return
     */
    @NonNull
    public Map<String, List<String>> retrieveAppClassificationTopics() {
        // appTopicsMap = Map<App, List<Topic>>
        Map<String, List<String>> appTopicsMap = new ArrayMap<>();
        String line;

        try (InputStreamReader inputStreamReader =
                     new InputStreamReader(mAssetManager.open(TOP_APP_FILE_PATH))) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            // Skip first line (columns name)
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(DELIMITER);

                //The first column name is app
                String app = columns[0];

                appTopicsMap.put(app, new ArrayList<>());
                for (int i = 1; i < columns.length; i++) {
                    // The other columns are topics of the app
                    appTopicsMap.get(app).add(columns[i]);
                }
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
