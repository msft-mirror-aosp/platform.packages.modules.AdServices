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
    private final List<Topic> mTopics;

    private GetTopicsResponse(@NonNull List<Topic> topics) {
        mTopics = topics;
    }

    @NonNull
    public List<Topic> getTopics() {
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
        return mTopics.equals(that.mTopics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTopics);
    }

    /**
     * Builder for {@link GetTopicsResponse} objects.
     * This class is unhidden so that developers can write tests.
     */
    public static final class Builder {
        private List<Topic> mTopics = new ArrayList<>();

        public Builder() {}

        /** Set the list of the returned Topics */
        public @NonNull Builder setTopics(@NonNull List<Topic> topics) {
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
            if (mTopics == null) {
                throw new IllegalArgumentException("Topics is null");
            }
            return new GetTopicsResponse(mTopics);
        }
    }
}