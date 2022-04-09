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
import android.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represent the result from the getTopics API.
 */
public class GetTopicsResponse {
    private final List<Long> mTaxonomyVersions;
    private final List<Long> mModelVersions;
    private final List<String> mTopics;
    private GetTopicsResponse(
            @NonNull List<Long> taxonomyVersions,
            @NonNull List<Long> modelVersions,
            @NonNull List<String> topics) {
        mTaxonomyVersions = taxonomyVersions;
        mModelVersions = modelVersions;
        mTopics = topics;
    }

    /** Get the Taxonomy Versions. */
    @NonNull
    public List<Long> getTaxonomyVersions() {
        return mTaxonomyVersions;
    }

    /** Get the Model Versions. */
    @NonNull
    public List<Long> getModelVersions() {
        return mModelVersions;
    }
    @NonNull
    public List<String> getTopics() {
        return mTopics;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GetTopicsResponse)) {
            return false;
        }
        GetTopicsResponse that = (GetTopicsResponse) o;
        return mTaxonomyVersions.equals(that.mTaxonomyVersions)
                && mModelVersions.equals(that.mModelVersions)
                && mTopics.equals(that.mTopics);
    }
    @Override
    public int hashCode() {
        return Objects.hash(mTaxonomyVersions, mModelVersions, mTopics);
    }

    /**
     * Builder for {@link GetTopicsResponse} objects.
     * This class is unhidden so that developers can write tests.
     */
    public static final class Builder {
        private List<Long> mTaxonomyVersions = new ArrayList<>();
        private List<Long> mModelVersions = new ArrayList<>();
        private List<String> mTopics = new ArrayList<>();
        public Builder() {}

        /** Set the Taxonomy Version. */
        public @NonNull Builder setTaxonomyVersions(@NonNull List<Long> taxonomyVersions) {
            mTaxonomyVersions = taxonomyVersions;
            return this;
        }

        /** Set the Model Version. */
        public @NonNull Builder setModelVersions(@NonNull List<Long> modelVersions) {
            mModelVersions = modelVersions;
            return this;
        }

        /** Set the list of the returned Topics */
        public @NonNull Builder setTopics(@NonNull List<String> topics) {
            mTopics = topics;
            return this;
        }

        /**
         * Builds a {@link GetTopicsResponse} instance.
         *
         * <p>throws IllegalArgumentException if any of the params are null or there is any mismatch
         * in the size of ModelVersions and TaxonomyVersions.
         */
        public @NonNull GetTopicsResponse build() {
            if (mTopics == null || mTaxonomyVersions == null || mModelVersions == null) {
                throw new IllegalArgumentException(
                        "Topics or TaxonomyVersion or ModelVersion is null");
            }
            if (mTopics.size() != mTaxonomyVersions.size()
                    || mTopics.size() != mModelVersions.size()) {
                throw new IllegalArgumentException("Size mismatch in Topics");
            }
            return new GetTopicsResponse(mTaxonomyVersions, mModelVersions, mTopics);
        }
    }
}