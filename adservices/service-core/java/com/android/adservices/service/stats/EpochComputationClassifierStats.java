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

/** Class for AdServicesEpochComputationClassifierReported atom. */
@AutoValue
public abstract class EpochComputationClassifierStats {

    /** @return list of topics returned by the classifier for each app. */
    public abstract ImmutableList<Integer> getTopicIds();

    /** @return build id of the assets. */
    public abstract int getBuildId();

    /** @return version of the assets used. */
    public abstract String getAssetVersion();

    /** @return type of the classifier used for classification. */
    public abstract int getClassifierType();

    /** @return on-device classifier status. */
    public abstract int getOnDeviceClassifierStatus();

    /** @return pre-computed classifier status. */
    public abstract int getPrecomputedClassifierStatus();

    /** @return generic builder. */
    public static EpochComputationClassifierStats.Builder builder() {
        return new AutoValue_EpochComputationClassifierStats.Builder();
    }

    /** Builder class for {@link EpochComputationClassifierStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set list of topics returned by the classifier for each app */
        public abstract EpochComputationClassifierStats.Builder setTopicIds(
                ImmutableList<Integer> value);

        /** Set duplicate topic count. */
        public abstract EpochComputationClassifierStats.Builder setBuildId(int value);

        /** Set version of the assets used. */
        public abstract EpochComputationClassifierStats.Builder setAssetVersion(String value);

        /** Set type of the classifier used for classification. */
        public abstract EpochComputationClassifierStats.Builder setClassifierType(int value);

        /** Set on-device classifier status. */
        public abstract EpochComputationClassifierStats.Builder setOnDeviceClassifierStatus(
                int value);

        /** Set pre-computed classifier status. */
        public abstract EpochComputationClassifierStats.Builder setPrecomputedClassifierStatus(
                int value);

        /** build for {@link EpochComputationClassifierStats}. */
        public abstract EpochComputationClassifierStats build();
    }
}
