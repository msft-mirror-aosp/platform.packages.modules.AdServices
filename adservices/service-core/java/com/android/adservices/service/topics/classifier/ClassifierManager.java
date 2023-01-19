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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.Flags.ClassifierType;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.PackageManagerUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Manager class to control the classifier behaviour between available types of classifier based on
 * classifier flags.
 */
public class ClassifierManager implements Classifier {
    private static ClassifierManager sSingleton;

    private Supplier<OnDeviceClassifier> mOnDeviceClassifier;
    private Supplier<PrecomputedClassifier> mPrecomputedClassifier;

    @VisibleForTesting
    ClassifierManager(
            @NonNull Supplier<OnDeviceClassifier> onDeviceClassifier,
            @NonNull Supplier<PrecomputedClassifier> precomputedClassifier) {
        mOnDeviceClassifier = onDeviceClassifier;
        mPrecomputedClassifier = precomputedClassifier;
    }

    /** Returns the singleton instance of the {@link ClassifierManager} given a context. */
    @NonNull
    public static ClassifierManager getInstance(@NonNull Context context) {
        synchronized (ClassifierManager.class) {
            if (sSingleton == null) {
                // Note: we need to have a singleton ModelManager shared by both Classifiers.
                sSingleton =
                        new ClassifierManager(
                                Suppliers.memoize(
                                        () ->
                                                new OnDeviceClassifier(
                                                        new Preprocessor(context),
                                                        new PackageManagerUtil(context),
                                                        new Random(),
                                                        ModelManager.getInstance(context),
                                                        CacheManager.getInstance(context),
                                                        AdServicesLoggerImpl.getInstance())),
                                Suppliers.memoize(
                                        () ->
                                                new PrecomputedClassifier(
                                                        ModelManager.getInstance(context),
                                                        CacheManager.getInstance(context),
                                                        AdServicesLoggerImpl.getInstance())));
            }
        }
        return sSingleton;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Invokes a particular {@link Classifier} instance based on the classifier type flag values.
     */
    @Override
    public Map<String, List<Topic>> classify(Set<String> apps) {
        @ClassifierType int classifierTypeFlag = FlagsFactory.getFlags().getClassifierType();
        if (classifierTypeFlag == Flags.PRECOMPUTED_CLASSIFIER) {
            return mPrecomputedClassifier.get().classify(apps);
        } else if (classifierTypeFlag == Flags.ON_DEVICE_CLASSIFIER) {
            return mOnDeviceClassifier.get().classify(apps);
        } else {
            // PRECOMPUTED_THEN_ON_DEVICE
            // Default if classifierTypeFlag value is not set/invalid.
            // precomputedClassifications expects non-empty values.
            Map<String, List<Topic>> precomputedClassifications =
                    mPrecomputedClassifier.get().classify(apps);
            // Collect package names that do not have any topics in the precomputed list.
            Set<String> remainingApps =
                    apps.stream()
                            .filter(
                                    packageName ->
                                            !isValidValue(packageName, precomputedClassifications))
                            .collect(toSet());
            Map<String, List<Topic>> onDeviceClassifications =
                    mOnDeviceClassifier.get().classify(remainingApps);

            // Combine classification values. On device classifications are used for values that
            // do not have valid precomputed classifications.
            Map<String, List<Topic>> combinedClassifications =
                    Stream.concat(
                                    onDeviceClassifications.entrySet().stream(),
                                    precomputedClassifications.entrySet().stream())
                            .collect(
                                    toMap(
                                            Entry::getKey,
                                            Entry::getValue,
                                            ClassifierManager::combineTopics));
            return combinedClassifications;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Invokes a particular {@link Classifier} instance based on the classifier type flag values.
     */
    @Override
    public List<Topic> getTopTopics(
            Map<String, List<Topic>> appTopics, int numberOfTopTopics, int numberOfRandomTopics) {
        @ClassifierType int classifierTypeFlag = FlagsFactory.getFlags().getClassifierType();
        // getTopTopics has the same implementation.
        // If the loaded assets are same, the output will be same.
        // TODO(b/240478024): Unify asset loading for Classifiers to ensure same assets are used.
        if (classifierTypeFlag == Flags.ON_DEVICE_CLASSIFIER) {
            return mOnDeviceClassifier
                    .get()
                    .getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);
        } else {
            // Use getTopics from PrecomputedClassifier as default.
            // TODO(b/240478024): Unify asset loading for Classifiers to ensure same assets are
            //  used.
            return mPrecomputedClassifier
                    .get()
                    .getTopTopics(appTopics, numberOfTopTopics, numberOfRandomTopics);
        }
    }

    // Prefer precomputed values for topics if the list is not empty.
    private static List<Topic> combineTopics(
            List<Topic> onDeviceValue, List<Topic> precomputedValue) {
        if (!precomputedValue.isEmpty()) {
            return precomputedValue;
        }
        return onDeviceValue;
    }

    // Return true if package name has non-empty list of topics in the classifications.
    private boolean isValidValue(String packageName, Map<String, List<Topic>> classifications) {
        if (classifications.containsKey(packageName)
                && !classifications.get(packageName).isEmpty()) {
            return true;
        }
        return false;
    }
}
