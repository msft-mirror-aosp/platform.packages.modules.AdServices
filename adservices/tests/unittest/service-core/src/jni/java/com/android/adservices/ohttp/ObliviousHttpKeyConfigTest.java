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

package com.android.adservices.ohttp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.io.BaseEncoding;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.spec.InvalidKeySpecException;

@ExtendedMockitoRule.SpyStatic(FlagsFactory.class)
public final class ObliviousHttpKeyConfigTest extends AdServicesExtendedMockitoTestCase {

    @Before
    public void setExpectations() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void create_configuresKeyCorrectly() throws InvalidKeySpecException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        assertThat(keyConfig.keyId()).isEqualTo(0X01);
        assertThat(keyConfig.kemId()).isEqualTo(0X0020);
        String publicKeyHex = BaseEncoding.base16().lowerCase().encode(keyConfig.getPublicKey());
        assertThat(publicKeyHex)
                .isEqualTo("31e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155");
        assertThat(keyConfig.kdfId()).isEqualTo(0X0001);
        assertThat(keyConfig.aeadId()).isEqualTo(0X0001);
    }

    @Test
    public void create_emptyKeyConfig_throwsError() {
        String keyConfigHex = "";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void create_wrongKemIdLength_throwsError() {
        String keyConfigHex = "0100";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void create_unsupportedKemId_throwsError() {
        String keyConfigHex = "010002";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void create_wrongPublicKeyLength_throwsError() {
        String keyConfigHex =
                "01000231e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e7981";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void create_wrongAlgorithmsLength_throwsError() {
        String keyConfigHex =
                "01000231e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e79815500";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void create_incompleteAlgorithmsList_throwsError() {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "000800010001";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        assertThrows(
                InvalidKeySpecException.class,
                () -> ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes));
    }

    @Test
    public void serializeOhttpPayloadHeader_returnsCorrectHeader() throws InvalidKeySpecException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);
        byte[] ohttpPayloadHeader = keyConfig.serializeOhttpPayloadHeader();

        assertThat(BaseEncoding.base16().lowerCase().encode(ohttpPayloadHeader))
                .isEqualTo("01002000010001");
    }

    @Test
    public void createRecipientKeyInfoBytes_returnsCorrectInfo()
            throws InvalidKeySpecException, IOException {
        boolean hasMediaTypeChanged = false;
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);
        RecipientKeyInfo response = keyConfig.createRecipientKeyInfo(hasMediaTypeChanged);

        assertThat(BaseEncoding.base16().lowerCase().encode(response.getBytes()))
                .isEqualTo("6d6573736167652f626874747020726571756573740001002000010001");
    }

    @Test
    public void createRecipientKeyInfoBytes_returnsCorrectInfo_withServerAuctionMediaTypeChange()
            throws InvalidKeySpecException, IOException {
        boolean hasMediaTypeChanged = true;
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);

        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);
        RecipientKeyInfo response = keyConfig.createRecipientKeyInfo(hasMediaTypeChanged);

        assertThat(BaseEncoding.base16().lowerCase().encode(response.getBytes()))
                .isEqualTo("6d6573736167652f61756374696f6e20726571756573740001002000010001");
    }

    @Test
    public void serializeKeyConfigToBytes_fromBuilder_getCorrectSerialization() {
        ObliviousHttpKeyConfig keyConfig =
                ObliviousHttpKeyConfig.builder()
                        .setKeyId(1)
                        .setKemId(32)
                        .setPublicKey(
                                BaseEncoding.base16()
                                        .lowerCase()
                                        .decode(
                                                "31e1f05a740102115220e9af918"
                                                        + "f738674aec95f54db6e04eb705aae8e798155"))
                        .setKdfId(1)
                        .setAeadId(1)
                        .build();

        byte[] serializedBytes = keyConfig.serializeKeyConfigToBytes();

        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "000400010001";
        assertThat(BaseEncoding.base16().lowerCase().encode(serializedBytes))
                .isEqualTo(keyConfigHex);
    }

    @Test
    public void serializeKeyConfigToBytes_fromSerializedKeyConfig_getSameKey() throws Exception {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "000400010001";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        byte[] serializedBytes = keyConfig.serializeKeyConfigToBytes();

        assertThat(BaseEncoding.base16().lowerCase().encode(serializedBytes))
                .isEqualTo(keyConfigHex);
    }

    @Test
    public void serializeKeyConfigToBytes_multipleAlgorithms_returnsSerializationWithOneSet()
            throws Exception {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100110001";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        byte[] serializedBytes = keyConfig.serializeKeyConfigToBytes();

        String expectedKeyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "000400010001";
        assertThat(BaseEncoding.base16().lowerCase().encode(serializedBytes))
                .isEqualTo(expectedKeyConfigHex);
    }
}
