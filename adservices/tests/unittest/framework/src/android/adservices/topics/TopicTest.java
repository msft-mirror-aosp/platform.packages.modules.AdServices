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

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link android.adservices.topics.Topic} */
public final class TopicTest extends AdServicesUnitTestCase {

    private static final long TAXONOMY_VERSION_2 = 2L;
    private static final long MODEL_VERSION_5 = 5L;
    private static final int TOPIC_ID_1 = 1;

    private Topic mTopic1;
    private Topic mTopic2;

    @Before
    public void setup() throws Exception {
        generateTopics();
    }

    @Test
    public void testGetters() {
        expect.that(mTopic1.getTopicId()).isEqualTo(1);
        expect.that(mTopic1.getModelVersion()).isEqualTo(5L);
        expect.that(mTopic1.getTaxonomyVersion()).isEqualTo(2L);
    }

    @Test
    public void testToString() {
        String expectedTopicString = "Topic{mTaxonomyVersion=2, mModelVersion=5, mTopicCode=1}";
        expect.that(mTopic1.toString()).isEqualTo(expectedTopicString);
        expect.that(mTopic2.toString()).isEqualTo(expectedTopicString);
    }

    @Test
    public void testEquals() {
        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(mTopic1, mTopic2);
    }

    @Test
    public void testEquals_nullObject() {
        // To test code won't throw if comparing to a null object.
        assertThat(mTopic1).isNotEqualTo(null);
    }

    @Test
    public void testNotEquals() {
        Topic different =
                new Topic(
                        /* mTaxonomyVersion */ 100L, /* mModelVersion */ 101L, /* mTopicId */ 102);
        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(mTopic1, different);
    }

    private void generateTopics() {
        mTopic1 = new Topic(TAXONOMY_VERSION_2, MODEL_VERSION_5, TOPIC_ID_1);
        mTopic2 = new Topic(TAXONOMY_VERSION_2, MODEL_VERSION_5, TOPIC_ID_1);
    }
}
