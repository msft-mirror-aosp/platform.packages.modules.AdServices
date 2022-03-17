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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Topics Classifier.
 *
 * This Classifier will classify app into list of Topics.
 */
public class Classifier {

    private static Classifier sSingleton;

    private Classifier() {}

    /** Returns an instance of the EpochManager given a context. */
    @NonNull
    public static Classifier getInstance() {
        synchronized (Classifier.class) {
            if (sSingleton == null) {
                sSingleton = new Classifier();
            }
            return sSingleton;
        }
    }

    /**
     * @return {@code appClassificationTopicsMap = Map<App, List<Topic>>}
     */
    @NonNull
    public Map<String, List<String>> classify(@NonNull Set<String> apps) {
        // Implement this.
        return new HashMap<>();
    }

    /**
     *
     * @return the list of Top Topics.
     */
    @NonNull
    public List<String> getTopTopics(int numberOfTopTopics, int numberOfRandomTopics) {
        // Implement this.
        return Arrays.asList("topic1", "topic2", "topic3", "topic4", "topic5", "random_topic");
    }
}
