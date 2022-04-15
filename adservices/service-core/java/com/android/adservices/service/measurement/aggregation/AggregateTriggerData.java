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

package com.android.adservices.service.measurement.aggregation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * POJO for AggregateTriggerData.
 */
public class AggregateTriggerData {

    private AttributionAggregatableKey mKey;
    private Set<String> mSourceKeys;

    private AggregateTriggerData() {
        mKey = null;
        mSourceKeys = new HashSet<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateTriggerData)) {
            return false;
        }
        AggregateTriggerData attributionTriggerData = (AggregateTriggerData) obj;
        return Objects.equals(mKey, attributionTriggerData.mKey)
                && Objects.equals(mSourceKeys, attributionTriggerData.mSourceKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mSourceKeys);
    }

    /**
     * Returns trigger_data's key which will be used to generate the aggregate key.
     */
    public AttributionAggregatableKey getKey() {
        return mKey;
    }

    /**
     * Returns the source_key set which represent which source this dimension applies to.
     */
    public Set<String> getSourceKeys() {
        return mSourceKeys;
    }

    /**
     * Builder for {@link AggregateTriggerData}.
     */
    public static final class Builder {
        private final AggregateTriggerData mBuilding;

        public Builder() {
            mBuilding = new AggregateTriggerData();
        }

        /**
         * See {@link AggregateTriggerData#getKey()}.
         */
        public Builder setKey(AttributionAggregatableKey key) {
            mBuilding.mKey = key;
            return this;
        }

        /**
         * See {@link AggregateTriggerData#getSourceKeys()}.
         */
        public Builder setSourceKeys(Set<String> sourceKeys) {
            mBuilding.mSourceKeys = sourceKeys;
            return this;
        }

        /**
         * Build the {@link AggregateTriggerData}
         */
        public AggregateTriggerData build() {
            return mBuilding;
        }
    }
}
