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

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Unit tests for {@link android.adservices.topics.GetTopicsResponse} */
@RequiresSdkLevelAtLeastS
public final class GetTopicsResponseTest extends AdServicesUnitTestCase {
    private static final List<Topic> TOPICS_LIST =
            List.of(new Topic(/* mTaxonomyVersion */ 1L, /* mModelVersion */ 1L, /* mTopicId */ 0));
    private static final List<EncryptedTopic> ENCRYPTED_TOPICS_LIST =
            List.of(
                    new EncryptedTopic(
                            /* mEncryptedTopic */ "cipherText".getBytes(StandardCharsets.UTF_8),
                            /* mKeyIdentifier */ "publicKey",
                            /* mEncapsulatedKey */ "encapsulatedKey"
                                    .getBytes(StandardCharsets.UTF_8)));

    @Test
    public void testGetTopicsResponseBuilder_nullableThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new GetTopicsResponse.Builder(null, ENCRYPTED_TOPICS_LIST).build());
        assertThrows(
                NullPointerException.class,
                () -> new GetTopicsResponse.Builder(TOPICS_LIST, null).build());
    }

    @Test
    public void testGetTopicsResponseBuilder_emptyListsAllowed() {
        // Verify empty list for plain text Topics is allowed.
        assertThat(
                        new GetTopicsResponse.Builder(List.of(), ENCRYPTED_TOPICS_LIST)
                                .build()
                                .getEncryptedTopics())
                .isEqualTo(ENCRYPTED_TOPICS_LIST);
    }

    @Test
    public void testGetTopicsResponseBuilder() {
        // Build GetTopicsResponse using topicList
        GetTopicsResponse response =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();

        // Validate the topicList is same to what we created
        expect.that(response.getTopics()).isEqualTo(TOPICS_LIST);
        // Validate the encryptedTopicList is same to what we created
        expect.that(response.getEncryptedTopics()).isEqualTo(ENCRYPTED_TOPICS_LIST);
    }

    @Test
    public void testEquals() {
        GetTopicsResponse getTopicsResponse1 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();
        GetTopicsResponse getTopicsResponse2 =
                new GetTopicsResponse.Builder(TOPICS_LIST, ENCRYPTED_TOPICS_LIST).build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(getTopicsResponse1, getTopicsResponse2);
    }
}

