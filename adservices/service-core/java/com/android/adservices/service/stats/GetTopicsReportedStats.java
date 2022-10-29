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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** Class for AdServicesGetTopicsReported atom. */
@AutoValue
public abstract class GetTopicsReportedStats {
    /** @return the list of topic ids. */
    public abstract ImmutableList<Integer> getTopicIds();

    /** @return number of topic ids filtered due to duplication. */
    public abstract int getDuplicateTopicCount();

    /** @return number of topic ids filtered due to being blocked. */
    public abstract int getFilteredBlockedTopicCount();

    /** @return generic builder. */
    public static GetTopicsReportedStats.Builder builder() {
        return new AutoValue_GetTopicsReportedStats.Builder();
    }

    /** Builder class for {@link GetTopicsReportedStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set topic ids. */
        public abstract GetTopicsReportedStats.Builder setTopicIds(ImmutableList<Integer> value);

        /** Set duplicate topic count. */
        public abstract GetTopicsReportedStats.Builder setDuplicateTopicCount(int value);

        /** Set filtered blocked topic count. */
        public abstract GetTopicsReportedStats.Builder setFilteredBlockedTopicCount(int value);

        /** build for {@link GetTopicsReportedStats}. */
        public abstract GetTopicsReportedStats build();
    }
}
