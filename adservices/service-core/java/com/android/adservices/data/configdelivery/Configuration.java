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

package com.android.adservices.data.configdelivery;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** Represents a configuration item with an ID and a Protobuf 'Any' value. */
public class Configuration {
    private final String id;
    private final Any any;

    public Configuration(String id, Any any) {
        this.id = id;
        this.any = any;
    }

    /**
     * Returns the ID of the configuration.
     *
     * @return The configuration ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves and parses the configuration value from {@link com.google.protobuf.Any} object into
     * the instance type provided.
     *
     * @param <T> The expected type of the configuration value (must extend {@link
     *     com.google.protobuf.MessageLite}).
     * @param instance An instance of the expected type (e.g., `RbEnrollment.getDefaultInstance()`).
     *     This is used to get the parser.
     * @return The unpacked configuration value as type {@code T}, or {@code null} if the 'any'
     *     field is null or if parsing fails.
     */
    @Nullable
    public <T extends MessageLite> T getValue(T instance) {
        try {
            if (any == null) {
                return null;
            }
            return getParser(instance).parseFrom(any.toByteArray());
        } catch (InvalidProtocolBufferException e) {
            // TODO(b/397634660): Add a ErrorLogUtil log
            LogUtil.e(
                    e,
                    "Unable to parse configuration proto com.google.protobuf.Any field value for id"
                            + " %s",
                    id);
            return null;
        }
    }

    private static <T extends MessageLite> Parser<T> getParser(T templateInstance) {
        return (Parser<T>) templateInstance.getParserForType();
    }
}
