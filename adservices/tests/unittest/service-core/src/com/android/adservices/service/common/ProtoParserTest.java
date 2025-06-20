/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.service.proto.config_delivery.MddConfigs;

import org.junit.Test;

public final class ProtoParserTest {

    private static final MddConfigs TEST_MDD_CONFIG_PROTO =
            MddConfigs.newBuilder()
                    .addMddConfigs(MddConfigs.MddConfig.newBuilder().setManifestId("1"))
                    .build();

    // Base 64 encoded MddConfigs Proto with, 1 MddConfig with Manifest Id set to 1
    private static final String TEST_BASE64_ENCODED_MDD_CONFIG_PROTO = "CgMKATE";

    @Test
    public void testParseProtoFromBase64_withNullInput_returnsNull() {
        MddConfigs mddConfigs = ProtoParserUtil.fromBase64(null, MddConfigs.parser());

        assertThat(mddConfigs).isNull();
    }

    @Test
    public void testParseProtoFromBase64_withEmptyInput_returnsNull() {
        MddConfigs mddConfigs = ProtoParserUtil.fromBase64("", MddConfigs.parser());

        assertThat(mddConfigs).isNull();
    }

    @Test
    public void testParseProtoFromBase64_withValidInput_returnsParsedProto() {
        MddConfigs mddConfigs =
                ProtoParserUtil.fromBase64(
                        TEST_BASE64_ENCODED_MDD_CONFIG_PROTO, MddConfigs.parser());

        assertThat(mddConfigs).isNotNull();
        assertThat(mddConfigs.getMddConfigs(0).getManifestId()).isEqualTo("1");
    }

    @Test
    public void toBase64_withValidInput_returnsBase64() {
        String base64 = ProtoParserUtil.toBase64(TEST_MDD_CONFIG_PROTO);

        assertThat(base64).isEqualTo(TEST_BASE64_ENCODED_MDD_CONFIG_PROTO);
    }
}
