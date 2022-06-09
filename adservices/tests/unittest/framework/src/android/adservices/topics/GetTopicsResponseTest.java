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

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsResponse}
 */
@SmallTest
public final class GetTopicsResponseTest {

    @Test
    public void testGetTopicsResponseBuilder_nullableThrows() throws Exception {
        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder().setTopics(null).build();
            });

        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder().setTaxonomyVersions(null).build();
            });

        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder().setModelVersions(null).build();
            });

        // This should not throw.
        GetTopicsResponse unusedResponse =
            new GetTopicsResponse.Builder()
                // Not setting anything default to empty.
                .build();
    }

    @Test
    public void testGetTopicsResponseBuilder_misMatchSizeThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L))
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList(1, 2))
                        .build();
            });

        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder()
                        // Not setting TaxonomyVersions implies empty.
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList(1, 2))
                        .build();
            });

        assertThrows(
            IllegalArgumentException.class,
            () -> {
                GetTopicsResponse unusedResponse =
                    new GetTopicsResponse.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L))
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList(1))
                        .build();
            });
    }
}

