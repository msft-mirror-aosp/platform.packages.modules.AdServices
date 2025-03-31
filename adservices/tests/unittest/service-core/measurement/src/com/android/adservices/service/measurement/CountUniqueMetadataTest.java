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

package com.android.adservices.service.measurement;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import org.junit.Test;

import java.util.Set;

public class CountUniqueMetadataTest {
    @Test
    public void testCreation() throws Exception {
        CountUniqueMetadata countUniqueMetadata = createCountUniqueMetadata();
        assertThat(countUniqueMetadata.getKey()).isEqualTo("key1");
        assertThat(countUniqueMetadata.getReportingOrigin())
                .isEqualTo(Uri.parse("https://example.test/cu"));
        assertThat(countUniqueMetadata.getValue()).isEqualTo(1);
        assertThat(countUniqueMetadata.getExpirationTime()).isEqualTo(234L);
        assertThat(countUniqueMetadata.getRegistrant())
                .isEqualTo(Uri.parse("android-app://com.example"));
    }

    @Test
    public void testDefaults() throws Exception {
        CountUniqueMetadata countUniqueMetadata = new CountUniqueMetadata.Builder().build();
        assertThat(countUniqueMetadata.getKey()).isNull();
        assertThat(countUniqueMetadata.getReportingOrigin()).isNull();
        assertThat(countUniqueMetadata.getValue()).isNull();
        assertThat(countUniqueMetadata.getExpirationTime()).isNull();
        assertThat(countUniqueMetadata.getRegistrant()).isNull();
    }

    @Test
    public void testHashCode_equals() throws Exception {
        CountUniqueMetadata countUniqueMetadata1 = createCountUniqueMetadata();
        CountUniqueMetadata countUniqueMetadata2 = createCountUniqueMetadata();
        Set<CountUniqueMetadata> countUniqueMetadataSet1 = Set.of(countUniqueMetadata1);
        Set<CountUniqueMetadata> countUniqueMetadataSet2 = Set.of(countUniqueMetadata2);
        assertThat(countUniqueMetadata2.hashCode()).isEqualTo(countUniqueMetadata1.hashCode());
        assertThat(countUniqueMetadata2).isEqualTo(countUniqueMetadata1);
        assertThat(countUniqueMetadataSet2).isEqualTo(countUniqueMetadataSet1);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        CountUniqueMetadata countUniqueMetadata1 = createCountUniqueMetadata();
        CountUniqueMetadata countUniqueMetadata2 =
                new CountUniqueMetadata.Builder()
                        .setKey("key2")
                        .setReportingOrigin(Uri.parse("https://example.test/cu"))
                        .setValue(2)
                        .setExpirationTime(234L)
                        .setRegistrant(Uri.parse("android-app://com.example2"))
                        .build();
        Set<CountUniqueMetadata> countUniqueMetadataSet1 = Set.of(countUniqueMetadata1);
        Set<CountUniqueMetadata> countUniqueMetadataSet2 = Set.of(countUniqueMetadata2);
        assertThat(countUniqueMetadata2.hashCode()).isNotEqualTo(countUniqueMetadata1.hashCode());
        assertThat(countUniqueMetadata2).isNotEqualTo(countUniqueMetadata1);
        assertThat(countUniqueMetadataSet2).isNotEqualTo(countUniqueMetadataSet1);
    }

    private CountUniqueMetadata createCountUniqueMetadata() {
        return new CountUniqueMetadata.Builder()
                .setKey("key1")
                .setReportingOrigin(Uri.parse("https://example.test/cu"))
                .setValue(1)
                .setExpirationTime(234L)
                .setRegistrant(Uri.parse("android-app://com.example"))
                .build();
    }
}
