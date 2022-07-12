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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;


import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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


        // This should not throw.
        GetTopicsResponse unusedResponse =
            new GetTopicsResponse.Builder()
                // Not setting anything default to empty.
                .build();
    }

    @Test
    public void testGetTopicsResponseBuilder() throws Exception {
        Topic topic =
                new Topic(/* mTaxonomyVersion */ 1L, /* mModelVersion */ 1L, /* mTopicId */ 0);
        List<Topic> topicList = new ArrayList<>();
        topicList.add(topic);

        // Build GetTopicsResponse using topicList
        GetTopicsResponse response = new GetTopicsResponse.Builder().setTopics(topicList).build();

        // Validate the topicList is same to what we created
        assertEquals(topicList, response.getTopics());
    }
}

