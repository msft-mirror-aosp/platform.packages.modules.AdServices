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

package com.android.adservices.service.topics;

import android.annotation.NonNull;
import android.text.TextUtils;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesConfig;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** A class to manage Epoch computation. */
class EpochManager {

    // TODO(b/223915674): make this configurable.
    // The Top Topics will have 6 topics.
    // The first 5 topics are the Top Topics derived by ML, and the 6th is a random topic from
    // taxonomy.
    // The index starts from 0.
    private static final int RANDOM_TOPIC_INDEX = 5;

    // TODO(b/223916172): make this configurable.
    // The number of top Topics not including the random one.
    private static final int NUM_TOP_TOPICS_NOT_INCLUDING_RANDOM_ONE = 5;

    private static EpochManager sSingleton;

    private final Random mRandom;

    private EpochManager(@NonNull Random random) {
        mRandom = random;
    }

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static EpochManager getInstance() {
        synchronized (EpochManager.class) {
            if (sSingleton == null) {
                sSingleton = new EpochManager(new Random());
            }
            return sSingleton;
        }
    }

    /**
     * Returns an instance of the EpochManager given a context. Not using Singleton so that we can
     * return different instances of EpochManager used for test
     */
    @NonNull
    public static EpochManager getInstanceForTest(@NonNull Random random) {
        return new EpochManager(random);
    }

    // Return a Map from Topic to set of App or Sdk that can learn about that topic.
    // This is similar to the table Can Learn Topic in the explainer.
    // Return Map<Topic, Set<Caller>>  where Caller = App or Sdk.
    @VisibleForTesting
    @NonNull
    static Map<String, Set<String>> computeCallersCanLearnMap(
            @NonNull Map<String, List<String>> appSdksUsageMap,
            @NonNull Map<String, List<String>> appClassificationTopicsMap) {
        Objects.requireNonNull(appSdksUsageMap);
        Objects.requireNonNull(appClassificationTopicsMap);

        // Map from Topic to set of App or Sdk that can learn about that topic.
        // This is similar to the table Can Learn Topic in the explainer.
        // Map<Topic, Set<Caller>>  where Caller = App or Sdk.
        Map<String, Set<String>> callersCanLearnMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();
            List<String> appTopics = entry.getValue();
            if (appTopics == null) {
                LogUtil.e("Can't find the Classification Topics for app = " + app);
                continue;
            }

            for (String topic : appTopics) {
                if (!callersCanLearnMap.containsKey(topic)) {
                    callersCanLearnMap.put(topic, new HashSet<>());
                }

                // All SDKs in the app can learn this topic too.
                for (String sdk : appSdksUsageMap.get(app)) {
                    if (TextUtils.isEmpty(sdk)) {
                        // Empty sdk means the app called the Topics API directly.
                        // Caller = app
                        // Then the app can learn its topic.
                        callersCanLearnMap.get(topic).add(app);
                    } else {
                        // Caller = sdk
                        callersCanLearnMap.get(topic).add(sdk);
                    }
                }
            }
        }

        return callersCanLearnMap;
    }

    // Return a random topics from the Top Topics.
    // The Top Topics include the Top 5 Topics and one random topic from the Taxonomy.
    @VisibleForTesting
    String selectRandomTopic(List<String> topTopics) {
        Preconditions.checkArgument(topTopics.size() == 6);
        int random = mRandom.nextInt(100);

        // For 5%, get the random topic.
        if (random < AdServicesConfig.getTopicsPercentageForRandomTopic()) {
            // The random topic is the last one on the list.
            return topTopics.get(RANDOM_TOPIC_INDEX);
        }

        // For 95%, pick randomly one out of 5 top topics.
        return topTopics.get(random % NUM_TOP_TOPICS_NOT_INCLUDING_RANDOM_ONE);
    }
}
