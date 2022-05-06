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

package com.android.adservices.data.topics;

import android.annotation.NonNull;

import java.util.Objects;

/**
 * POJO Represents a Topic.
 *
 * @hide
 */
public class Topic {
    private final String mTopic;
    private final long mTaxonomyVersion;
    private final long mModelVersion;

    public Topic(@NonNull String topic, long taxonomyVersion, long modelVersion) {
        mTopic = topic;
        mTaxonomyVersion = taxonomyVersion;
        mModelVersion = modelVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Topic))  {
            return false;
        }
        Topic topic = (Topic) o;
        return mTaxonomyVersion == topic.mTaxonomyVersion && mModelVersion == topic.mModelVersion
                && mTopic.equals(topic.mTopic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTopic, mTaxonomyVersion, mModelVersion);
    }

    public String getTopic() {
        return mTopic;
    }

    public long getTaxonomyVersion() {
        return mTaxonomyVersion;
    }

    public long getModelVersion() {
        return mModelVersion;
    }
}
