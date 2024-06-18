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

import static com.android.adservices.ohttp.ObliviousHttpTestFixtures.getTestVectors;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.io.BaseEncoding;
import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

@ExtendedMockitoRule.SpyStatic(FlagsFactory.class)
public class ObliviousHttpClientTest extends AdServicesExtendedMockitoTestCase {
    @Mock private Flags mMockFlags;

    @Before
    public void setExpectations() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void create_unsupportedAlgorithms_throwsError() throws InvalidKeySpecException {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080005000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        ObliviousHttpKeyConfig keyConfig = ObliviousHttpKeyConfig.fromSerializedKeyConfig(bytes);

        Assert.assertThrows(
                UnsupportedHpkeAlgorithmException.class,
                () -> ObliviousHttpClient.create(keyConfig));
    }

    @Test
    public void create_supportedAlgorithms_clientCreatedSuccessfully()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        ObliviousHttpTestFixtures.OhttpTestVector testVector = getTestVectors().get(0);
        ObliviousHttpClient actualClient = ObliviousHttpClient.create(testVector.keyConfig);
        Truth.assertThat(actualClient).isNotNull();
    }

    @Test
    public void create_supportedAlgorithms_kemIdSetCorrectly()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        ObliviousHttpTestFixtures.OhttpTestVector testVector = getTestVectors().get(0);
        ObliviousHttpClient actualClient = ObliviousHttpClient.create(testVector.keyConfig);
        Assert.assertEquals(
                actualClient.getHpkeAlgorithmSpec().kem().identifier(),
                testVector.keyConfig.kemId());
    }

    @Test
    public void create_supportedAlgorithms_kdfIdSetCorrectly()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        ObliviousHttpTestFixtures.OhttpTestVector testVector = getTestVectors().get(0);
        ObliviousHttpClient actualClient = ObliviousHttpClient.create(testVector.keyConfig);
        Assert.assertEquals(
                actualClient.getHpkeAlgorithmSpec().kdf().identifier(),
                testVector.keyConfig.kdfId());
    }

    @Test
    public void create_supportedAlgorithms_aeadIdSetCorrectly()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        ObliviousHttpTestFixtures.OhttpTestVector testVector = getTestVectors().get(0);
        ObliviousHttpClient actualClient = ObliviousHttpClient.create(testVector.keyConfig);
        Assert.assertEquals(
                actualClient.getHpkeAlgorithmSpec().aead().identifier(),
                testVector.keyConfig.aeadId());
    }

    @Test
    public void createObliviousHttpRequest_testAllTestVectors()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        boolean hasMediaTypeChanged = false;
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors =
                getTestVectors(hasMediaTypeChanged);
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(
                            plainTextBytes, seedBytes, hasMediaTypeChanged);

            Assert.assertEquals(
                    testVector.expectedEnc,
                    BaseEncoding.base16()
                            .lowerCase()
                            .encode(
                                    request.requestContext()
                                            .encapsulatedSharedSecret()
                                            .getBytes()));
            Assert.assertEquals(
                    testVector.requestCipherText,
                    BaseEncoding.base16().lowerCase().encode(request.serialize()));
        }
    }

    @Test
    public void createObliviousHttpRequest_testAllTestVectors_withServerAuctionMediaTypeChange()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        boolean hasMediaTypeChanged = true;
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors =
                getTestVectors(hasMediaTypeChanged);
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(
                            plainTextBytes, seedBytes, hasMediaTypeChanged);

            Assert.assertEquals(
                    testVector.expectedEnc,
                    BaseEncoding.base16()
                            .lowerCase()
                            .encode(
                                    request.requestContext()
                                            .encapsulatedSharedSecret()
                                            .getBytes()));
            Assert.assertEquals(
                    testVector.requestCipherText,
                    BaseEncoding.base16().lowerCase().encode(request.serialize()));
        }
    }

    @Test
    public void decryptObliviousHttpResponse_testAllTestVectors()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        boolean hasMediaTypeChanged = false;
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors =
                getTestVectors(hasMediaTypeChanged);
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(
                            plainTextBytes, seedBytes, hasMediaTypeChanged);
            byte[] decryptedResponse =
                    client.decryptObliviousHttpResponse(
                            BaseEncoding.base16().lowerCase().decode(testVector.responseCipherText),
                            request.requestContext());

            Assert.assertEquals(
                    testVector.responsePlainText,
                    new String(decryptedResponse, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void decryptObliviousHttpResponse_testAllTestVectors_withServerAuctionMediaTypeChange()
            throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException, IOException {
        boolean hasMediaTypeChanged = true;
        List<ObliviousHttpTestFixtures.OhttpTestVector> testVectors =
                getTestVectors(hasMediaTypeChanged);
        for (ObliviousHttpTestFixtures.OhttpTestVector testVector : testVectors) {
            ObliviousHttpClient client = ObliviousHttpClient.create(testVector.keyConfig);
            String plainText = testVector.plainText;
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
            byte[] seedBytes = testVector.seed.getBytes(StandardCharsets.US_ASCII);

            ObliviousHttpRequest request =
                    client.createObliviousHttpRequest(
                            plainTextBytes, seedBytes, hasMediaTypeChanged);

            byte[] decryptedResponse =
                    client.decryptObliviousHttpResponse(
                            BaseEncoding.base16().lowerCase().decode(testVector.responseCipherText),
                            request.requestContext());

            Assert.assertEquals(
                    testVector.responsePlainText,
                    new String(decryptedResponse, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void decryptObliviousHttpResponse_withAuctionResult() throws Exception {
        boolean hasMediaTypeChanged = false;
        String keyConfigString =
                "0400206d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f"
                        + "012e76fcdcef62000400010002";
        ObliviousHttpKeyConfig keyConfig =
                ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                        BaseEncoding.base16().lowerCase().decode(keyConfigString));
        ObliviousHttpClient client = ObliviousHttpClient.create(keyConfig);

        String plainText = "doesnt matter";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpRequest request =
                client.createObliviousHttpRequest(plainTextBytes, seedBytes, hasMediaTypeChanged);

        String cipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c"
                    + "f964b13dc827c6e6b50c6f3ad47c9e75f4fa938531478fcaf95c500b694ce422e083d94cde6a"
                    + "5995d7f656868da9777fb32823f83652626ce837efc4a962069ab968e14206e83036a740767a"
                    + "a83e0572c44dd6b9d5cf7f9e895d641f3c0c85626fb8ff253c692a3ba76bc900e1da038216e1"
                    + "8ffabffc1d933683dd9621e29dab19173e261a6b16f73277448d43c814b2532515729ed89eca"
                    + "7af797a80d3dd4c3965ee25f8b98c3e423e2ad12b27029c80358bb9f9d55b357ba88ec6e2aa"
                    + "8eabaf004b0c6fcff364388e72f3bcadd39923f2f3d2bfe8e15beb474d53f78f7a74295e13e8"
                    + "37256c756df";
        byte[] decryptedResponse =
                client.decryptObliviousHttpResponse(
                        BaseEncoding.base16().lowerCase().decode(cipherText),
                        request.requestContext());

        String expectedJsonString =
                "{\"adRenderUrl\":\"test-376003777.com\","
                        + "\"adComponentRenderUrls\":[],\"interestGroupName\":\"shoe\","
                        + "\"interestGroupOwner\":\"https://bid1.com\",\"score\":233,"
                        + "\"bid\":3,\"isChaff\":false,\"biddingGroups\":"
                        + "{\"https://bid1.com\":{\"index\":[0]}}}";

        Assert.assertEquals(
                expectedJsonString, new String(decryptedResponse, StandardCharsets.UTF_8));
    }

    @Test
    public void decryptObliviousHttpResponse_withAuctionResult_withServerAuctionMediaTypeChange()
            throws Exception {
        boolean hasMediaTypeChanged = true;
        String keyConfigString =
                "0400206d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f"
                        + "012e76fcdcef62000400010002";
        ObliviousHttpKeyConfig keyConfig =
                ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                        BaseEncoding.base16().lowerCase().decode(keyConfigString));
        ObliviousHttpClient client = ObliviousHttpClient.create(keyConfig);

        String plainText = "doesnt matter";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpRequest request =
                client.createObliviousHttpRequest(plainTextBytes, seedBytes, hasMediaTypeChanged);

        String cipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c"
                    + "ae7e11bfc23d3b8a0372497dfbdf06be1cbcb9b14a0eba7ebbb04af4d00b656ec95d638d4bb5"
                    + "ee69c3995e59190c662ab08e4277b7d8f8447c0188457b67f9ad2d84299d602b3464698606d9"
                    + "7ea380b27d439ba9495c07450f942e8bf45d6389d4ca588a1504fcc10e150ed6b0e84e516fa8"
                    + "0616982316d0415fbe0d6cba72d47d8a31a2a941696ca7cb63c1ea2e8dedd7a5dc760a7fe408"
                    + "cddbc90dbd13d9acccb1e8f8955ec26d2262f9712519137a0b0eaad200747b46943eb1372053"
                    + "f0c6fb8d7b6bdf27127b8d4d31a2f5becbb734591a3aef431675b6924ee206b42800d6ee169f"
                    + "4e5cfdcbd6";

        byte[] decryptedResponse =
                client.decryptObliviousHttpResponse(
                        BaseEncoding.base16().lowerCase().decode(cipherText),
                        request.requestContext());

        String expectedJsonString =
                "{\"adRenderUrl\":\"test-376003777.com\","
                        + "\"adComponentRenderUrls\":[],\"interestGroupName\":\"shoe\","
                        + "\"interestGroupOwner\":\"https://bid1.com\",\"score\":233,"
                        + "\"bid\":3,\"isChaff\":false,\"biddingGroups\":"
                        + "{\"https://bid1.com\":{\"index\":[0]}}}";

        Assert.assertEquals(
                expectedJsonString, new String(decryptedResponse, StandardCharsets.UTF_8));
    }
}
