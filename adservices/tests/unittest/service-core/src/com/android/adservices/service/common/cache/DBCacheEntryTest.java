/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.common.cache;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class DBCacheEntryTest extends AdServicesUnitTestCase {

    private static final ImmutableMap<String, List<String>> RESPONSE_HEADERS =
            ImmutableMap.of(
                    "header_1",
                    ImmutableList.of("h1_value1", "h1_value2"),
                    "header_2",
                    ImmutableList.of("h2_value1", "h2_value2"));
    private static final String SERIALIZED_HEADERS =
            "header_1=h1_value1,h1_value2;header_2=h2_value1,h2_value2";

    @Test
    public void testSerializationHeadersMap_Empty_Empty() {
        assertThat(DBCacheEntry.Converters.serializeResponseHeaders(ImmutableMap.of())).isEmpty();
    }

    @Test
    public void testSerializationHeadersMap_Null_Empty() {
        assertThat(DBCacheEntry.Converters.serializeResponseHeaders(null)).isEmpty();
    }

    @Test
    public void testSerializationHeadersMap_Valid_Success() {
        assertThat(DBCacheEntry.Converters.serializeResponseHeaders(RESPONSE_HEADERS))
                .isEqualTo(SERIALIZED_HEADERS);
    }

    @Test
    public void testSerializationHeadersMap_EmptyListSkipped_Success() {
        ImmutableMap<String, List<String>> mapWithEmptyList =
                ImmutableMap.of(
                        "header_1",
                        ImmutableList.of("h1_value1", "h1_value2"),
                        "header_2",
                        ImmutableList.of("h2_value1", "h2_value2"),
                        "Dummy_Key",
                        new ArrayList<>());
        assertThat(DBCacheEntry.Converters.serializeResponseHeaders(mapWithEmptyList))
                .isEqualTo(SERIALIZED_HEADERS);
    }

    @Test
    public void testDeSerializationHeadersMap_Empty_Empty() {
        assertThat(DBCacheEntry.Converters.deserializeResponseHeaders("")).isEmpty();
    }

    @Test
    public void testDeSerializationHeadersMap_Null_Empty() {
        assertThat(DBCacheEntry.Converters.deserializeResponseHeaders(null)).isEmpty();
    }

    @Test
    public void testDeSerializationHeadersMap_Valid_Success() {
        assertThat(DBCacheEntry.Converters.deserializeResponseHeaders(SERIALIZED_HEADERS))
                .isEqualTo(RESPONSE_HEADERS);
    }
}
