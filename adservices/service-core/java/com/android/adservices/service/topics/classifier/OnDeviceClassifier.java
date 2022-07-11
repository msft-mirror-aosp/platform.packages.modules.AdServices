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
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import com.android.adservices.LogUtil;
import com.android.adservices.service.topics.AppInfo;
import com.android.adservices.service.topics.PackageManagerUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.text.nlclassifier.BertNLClassifier;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Classifier classifies app into list of Topics using the on-device classification ML Model.
 */
public class OnDeviceClassifier implements Classifier {

    private static OnDeviceClassifier sSingleton;

    // TODO(b/235497008): Convert MAX_LABELS_PER_APP to a flag.
    @VisibleForTesting static final int MAX_LABELS_PER_APP = 10;

    private static final String EMPTY = "";
    private static final AppInfo EMPTY_APP_INFO = new AppInfo(EMPTY, EMPTY);
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";
    private static final String LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String NO_VERSION_INFO = "NO_VERSION_INFO";

    private final Preprocessor mPreprocessor;
    private final PackageManagerUtil mPackageManagerUtil;
    private final AssetManager mAssetManager;
    private final Random mRandom;

    private BertNLClassifier mBertNLClassifier;
    private ImmutableList<Integer> mLabels;
    private boolean mLoaded;
    private ImmutableMap<String, AppInfo> mAppInfoMap;

    public OnDeviceClassifier(
            @NonNull Preprocessor preprocessor,
            @NonNull PackageManagerUtil packageManagerUtil1,
            @NonNull AssetManager assetManager,
            @NonNull Random random) {
        mPreprocessor = preprocessor;
        mPackageManagerUtil = packageManagerUtil1;
        mAssetManager = assetManager;
        mRandom = random;
        mLoaded = false;
        mAppInfoMap = ImmutableMap.of();
    }

    /** Returns the singleton instance of the {@link OnDeviceClassifier} given a context. */
    @NonNull
    public static OnDeviceClassifier getInstance(@NonNull Context context) {
        synchronized (OnDeviceClassifier.class) {
            if (sSingleton == null) {
                sSingleton =
                        new OnDeviceClassifier(
                                new Preprocessor(context),
                                new PackageManagerUtil(context),
                                context.getAssets(),
                                new Random());
            }
        }
        return sSingleton;
    }

    @Override
    @NonNull
    public ImmutableMap<String, List<Integer>> classify(@NonNull Set<String> appPackageNames) {
        // Load the assets if not loaded already.
        if (!isLoaded()) {
            mLoaded = load();
        }

        // Load latest app info for every call.
        mAppInfoMap = mPackageManagerUtil.getAppInformation(appPackageNames);
        if (mAppInfoMap.isEmpty()) {
            LogUtil.w("Loaded app description map is empty.");
        }

        ImmutableMap.Builder<String, List<Integer>> packageNameToTopicIds = ImmutableMap.builder();
        for (String appPackageName : appPackageNames) {
            String appDescription = getProcessedAppDescription(appPackageName);
            List<Integer> appClassificationTopicIds = getAppClassificationTopics(appDescription);
            LogUtil.v(
                    "[ML] Top classification for app description \""
                            + appDescription
                            + "\" is "
                            + Iterables.getFirst(appClassificationTopicIds, /*default value*/ -1));
            packageNameToTopicIds.put(appPackageName, appClassificationTopicIds);
        }

        return packageNameToTopicIds.build();
    }

    @Override
    @NonNull
    public List<Integer> getTopTopics(
            Map<String, List<Integer>> appTopics, int numberOfTopTopics, int numberOfRandomTopics) {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        return CommonClassifierHelper.getTopTopics(
                appTopics, mLabels, mRandom, numberOfTopTopics, numberOfRandomTopics);
    }

    // Uses the BertNLClassifier to fetch the most relevant topic id based on the input app
    // description.
    private List<Integer> getAppClassificationTopics(@NonNull String appDescription) {
        // Returns list of labelIds with their corresponding score in Category for the app
        // description.
        List<Category> classifications = mBertNLClassifier.classify(appDescription);
        // Get the highest score first. Sort in decreasing order.
        classifications.sort(Comparator.comparing(Category::getScore).reversed());

        // Limit the number of entries to first MAX_LABELS_PER_APP.
        // TODO(b/235435229): Evaluate the strategy to use first x elements.
        return classifications.stream()
                .map(OnDeviceClassifier::convertCategoryLabelToTopicId)
                .limit(MAX_LABELS_PER_APP)
                .collect(Collectors.toList());
    }

    // Converts Category Label to TopicId. Expects label to be labelId of the classified category.
    // Returns -1 if conversion to int fails for the label.
    private static int convertCategoryLabelToTopicId(Category category) {
        try {
            // Category label is expected to be the topicId of the predicted topic.
            return Integer.parseInt(category.getLabel());
        } catch (NumberFormatException numberFormatException) {
            LogUtil.e(
                    numberFormatException,
                    "ML model did not return a topic id. Label returned is %s",
                    category.getLabel());
            return -1;
        }
    }

    // Fetch app description for the package and preprocess it for the ML model.
    private String getProcessedAppDescription(@NonNull String appPackageName) {
        // Fetch app description from the loaded map.
        AppInfo appInfo = mAppInfoMap.getOrDefault(appPackageName, EMPTY_APP_INFO);
        String appDescription = appInfo.getAppDescription();

        // Preprocess the app description for the model.
        appDescription = mPreprocessor.preprocessAppDescription(appDescription);
        appDescription = mPreprocessor.removeStopWords(appDescription);
        return appDescription;
    }

    long getBertModelVersion() {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        String modelVersion = mBertNLClassifier.getModelVersion();
        if (modelVersion.equals(NO_VERSION_INFO)) {
            return 0;
        }

        return Long.parseLong(modelVersion);
    }

    long getBertLabelsVersion() {
        // Load assets if necessary.
        if (!isLoaded()) {
            load();
        }

        String labelsVersion = mBertNLClassifier.getLabelsVersion();
        if (labelsVersion.equals(NO_VERSION_INFO)) {
            return 0;
        }

        return Long.parseLong(labelsVersion);
    }

    // Indicates whether assets are loaded.
    private boolean isLoaded() {
        return mLoaded;
    }

    // Loads classifier model evaluation assets needed for classification.
    // Return true on successful loading of all assets. Return false if any loading fails.
    private boolean load() {

        // Load Bert model.
        try {
            mBertNLClassifier = loadModel(MODEL_FILE_PATH);
        } catch (IOException e) {
            LogUtil.e(e, "Loading ML model failed.");
            return false;
        }

        // Load labels.
        mLabels = CommonClassifierHelper.retrieveLabels(mAssetManager, LABELS_FILE_PATH);

        return true;
    }

    // Load BertNLClassifier Model from the corresponding modelFilePath.
    private BertNLClassifier loadModel(String modelFilePath) throws IOException {
        return BertNLClassifier.createFromBuffer(getModel(modelFilePath));
    }

    // Load model as a ByteBuffer from the asset manager.
    private ByteBuffer getModel(String modelFilePath) throws IOException {
        AssetFileDescriptor fileDescriptor = mAssetManager.openFd(modelFilePath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
