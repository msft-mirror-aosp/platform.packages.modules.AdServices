/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.json.JSONException;

/** POJO for AggregatableKeyValue */
public class AggregatableKeyValue {
    int mValue;

    private AggregatableKeyValue(AggregatableKeyValue.Builder builder) {
        mValue = builder.mValue;
    }

    /** Returns the int value of aggregatable_value's value. */
    public int getValue() {
        return mValue;
    }

    public static final class Builder {
        private int mValue;

        public Builder(int value) throws JSONException {
            mValue = value;
        }

        /** Build the {@link AggregatableKeyValue}. */
        public AggregatableKeyValue build() {
            return new AggregatableKeyValue(this);
        }
    }
}
