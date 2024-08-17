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

package com.android.adservices.service.js;

/**
 * Used for marshalling an object {@code T} into JSON
 *
 * @param <T> The object to marshal into JSON.
 */
public interface AccumulatingJsonMarshaller<T> {

    /**
     * Marshals object {@code T} into JSON and adds it to the {@code accumulator}
     *
     * @param value The object to be marshaled into JSON
     * @param accumulator The {@link StringBuilder} to add the marshaled object to
     */
    void serializeEntryToJson(T value, StringBuilder accumulator);
}
