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
package android.adservices.topics;

import java.util.Objects;

/** Represent the topic result from the getTopics API. */
public final class Topic {
    private final long mTaxonomyVersion;
    private final long mModelVersion;
    private final int mTopicId;

    public Topic(long mTaxonomyVersion, long mModelVersion, int mTopicId) {
        this.mTaxonomyVersion = mTaxonomyVersion;
        this.mModelVersion = mModelVersion;
        this.mTopicId = mTopicId;
    }

    /** Get the ModelVersion. */
    public long getModelVersion() {
        return mModelVersion;
    }

    /** Get the TaxonomyVersion. */
    public long getTaxonomyVersion() {
        return mTaxonomyVersion;
    }

    /** Get the Topic Id. */
    public int getTopicId() {
        return mTopicId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || !(object instanceof Topic)) return false;
        if (!super.equals(object)) return false;
        Topic topic = (Topic) object;
        return mTaxonomyVersion == topic.mTaxonomyVersion
                && mModelVersion == topic.mModelVersion
                && mTopicId == topic.mTopicId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mTaxonomyVersion, mModelVersion, mTopicId);
    }

    @Override
    public java.lang.String toString() {
        return "Topic{"
                + "mTaxonomyVersion="
                + mTaxonomyVersion
                + ", mModelVersion="
                + mModelVersion
                + ", mTopicCode="
                + mTopicId
                + '}';
    }
}
