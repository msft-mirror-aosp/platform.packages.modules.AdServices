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

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.service.proto.RbEnrollment;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;

public class ConfigurationTest {

    @Test
    public void getIdAndValue_withValidAnyValue_returnsIdAndParsedValue()
            throws InvalidProtocolBufferException {
        Configuration configuration =
                new Configuration(
                        "id1_v1",
                        Any.parseFrom(
                                RbEnrollment.newBuilder()
                                        .setEnrolledSite("https://example.com")
                                        .build()
                                        .toByteArray()));

        RbEnrollment rbEnrollment = configuration.getValue(RbEnrollment.getDefaultInstance());

        assertThat(configuration.getId()).isEqualTo("id1_v1");
        assertThat(rbEnrollment.getEnrolledSite()).isEqualTo("https://example.com");
    }

    @Test
    public void getValue_withNullAny_returnsNull() {
        Configuration configuration = new Configuration("id1_v1", null);
        RbEnrollment rbEnrollment = configuration.getValue(RbEnrollment.getDefaultInstance());

        assertThat(configuration.getId()).isEqualTo("id1_v1");
        assertThat(rbEnrollment).isNull();
    }

    @Test
    public void getValue_withCorruptAnyValue_returnsNull() {
        Configuration configuration =
                new Configuration(
                        "id1_v1",
                        Any.newBuilder().setValue(ByteString.copyFrom(new byte[] {-1})).build());

        assertThat(configuration.getId()).isEqualTo("id1_v1");
        assertThat(configuration.getValue(RbEnrollment.getDefaultInstance())).isNull();
    }
}
