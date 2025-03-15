/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.signals;

import androidx.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import com.google.auto.value.AutoValue;

import org.json.JSONObject;

/** POJO Represents a set of signal updates bundled with related metadata. */
@Immutable
@AutoValue
public abstract class SignalUpdates {

    /** The signal update JSON. */
    @NonNull
    public abstract JSONObject getUpdateJson();

    /** The signal update schema version. */
    public abstract int getUpdateSchemaVersion();

    /**
     * @return a builder to create an instance of {@link SignalUpdates}
     */
    public static SignalUpdates.Builder builder() {
        return new AutoValue_SignalUpdates.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** For more details see {@link #getUpdateJson()}. */
        @NonNull
        public abstract Builder setUpdateJson(@NonNull JSONObject updateJson);

        /** For more details see {@link #getUpdateSchemaVersion()}. */
        @NonNull
        public abstract Builder setUpdateSchemaVersion(int updateSchemaVersion);

        /**
         * @return an instance of {@link SignalUpdates}.
         */
        @NonNull
        public abstract SignalUpdates build();
    }
}
