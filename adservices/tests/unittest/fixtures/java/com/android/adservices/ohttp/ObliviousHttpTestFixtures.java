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

import com.google.common.io.BaseEncoding;

import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

public class ObliviousHttpTestFixtures {
    public static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";

    public static final String SERVER_PRIVATE_KEY =
            "b77431ecfa8f4cfc30d6e467aafa06944dffe28cb9dd1409e33a3045f5adc8a1";

    /** Returns OHTTP test vectors for client and gateway testing */
    public static List<OhttpTestVector> getTestVectors() throws InvalidKeySpecException {
        return getTestVectors(false);
    }

    /** Returns OHTTP test vectors for client and gateway testing */
    public static List<OhttpTestVector> getTestVectors(boolean hasMediaTypeChanged)
            throws InvalidKeySpecException {
        List<OhttpTestVector> testVectors = new ArrayList<>();

        String expectedEnc, requestCipherText, responseCipherText;

        expectedEnc = "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67";
        if (hasMediaTypeChanged) {
            requestCipherText =
                    "00040020000100021cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d"
                        + "0b18cb9a6770fbc40afc43d174f4b43cad7157d7b82b42f00aba7333d5f6c998918cca";
            responseCipherText =
                    "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6ca139"
                            + "03afb02a309d166f727cf2dd0dbd3c06dde7a508e817cd29e83d5fd173";
        } else {
            requestCipherText =
                    "040020000100021cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d"
                        + "0b18cb9a672ef2da3b97acee493624b9959f0fc6df008a6f0701c923c5a60ed0ed2c34";
            responseCipherText =
                    "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                            + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        }
        testVectors.add(
                new OhttpTestVector(
                        /* keyId= */ 4,
                        /* seed= */ "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww",
                        expectedEnc,
                        /* plainText= */ "test request 1",
                        requestCipherText,
                        /* responsePlainText= */ "test response 1",
                        responseCipherText,
                        getKeyConfig(4)));

        expectedEnc = "4a4f8ccde198d66e99b4c014418a3223ce256c98900ae4a6811fd10f7eb84c2c";
        if (hasMediaTypeChanged) {
            requestCipherText =
                    "00060020000100024a4f8ccde198d66e99b4c014418a3223ce256c98900ae4a6811fd1"
                        + "0f7eb84c2cb91acd000554a7b6f780fc3d4e95e2e6aba492e426c8fb3f9601caf8cbda";
            responseCipherText =
                    "626262626262626262626262626262626262626262626262626262626262626265d4"
                            + "292fbb81f7d6fd6520a34a78bda1ce4caf6ec1e042606a9b8c33ac5201";
        } else {
            requestCipherText =
                    "060020000100024a4f8ccde198d66e99b4c014418a3223ce256c98900ae4a6811fd1"
                        + "0f7eb84c2cbf6ca81bd6badb87b1f44f6cd07b78a3f1653f810b3e7cc41c1876e2086a";
            responseCipherText =
                    "6262626262626262626262626262626262626262626262626262626262626262a22d"
                            + "516e6d6dcdc5766bdafa221535c6eeb5c760ec524ca8afda52446e176d";
        }
        testVectors.add(
                new OhttpTestVector(
                        /* keyId= */ 6,
                        /* seed= */ "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        expectedEnc,
                        /* plainText= */ "test request 2",
                        requestCipherText,
                        /* responsePlainText= */ "test response 2",
                        responseCipherText,
                        getKeyConfig(6)));

        expectedEnc = "ab4f197998fcc56cc6ed68c1d931af9bb522ec00743e181f7330915df4aa3176";
        if (hasMediaTypeChanged) {
            requestCipherText =
                    "0007002000010002ab4f197998fcc56cc6ed68c1d931af9bb522ec00743e181f733091"
                        + "5df4aa3176a0e324fc3694f13cb82e316ec079eff97376e4231f276e9739bc62091383";
            responseCipherText =
                    "62626262626262626262626262626262626262626262626262626262626262627e2a"
                            + "41113e495fb50bd9bc7c4c755fa9154d5d7553fc0fd294ab6112e27771";
        } else {
            requestCipherText =
                    "07002000010002ab4f197998fcc56cc6ed68c1d931af9bb522ec00743e181f733091"
                        + "5df4aa3176381c0cf6c37e022bed9cc4d5207f9655464c638648336863b09f3bab2f65";
            responseCipherText =
                    "62626262626262626262626262626262626262626262626262626262626262621985"
                            + "bad58d33af4bca24f175cec5b058c1f1318eabde53c48811c10c6125f8";
        }
        testVectors.add(
                new OhttpTestVector(
                        /* keyId= */ 7,
                        /* seed= */ "qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq",
                        expectedEnc,
                        /* plainText= */ "test request 3",
                        requestCipherText,
                        /* responsePlainText= */ "test response 3",
                        responseCipherText,
                        getKeyConfig(7)));

        expectedEnc = "815fb6314405e007d04ed215c223d5cd4b799d07bb7189ad10dbf324ea534271";
        if (hasMediaTypeChanged) {
            requestCipherText =
                    "0002002000010002815fb6314405e007d04ed215c223d5cd4b799d07bb7189ad10dbf3"
                        + "24ea534271f3c29847a565877bb415a1a9ab00fde1a5c8aaf5fe887a8112884c40ab70";
            responseCipherText =
                    "646464646464646464646464646464646464646464646464646464646464646492b1"
                            + "925a861bffc0bbb9a66bd07ef57b0c65ea636ae0ab3061ce502ec09c34";
        } else {
            requestCipherText =
                    "02002000010002815fb6314405e007d04ed215c223d5cd4b799d07bb7189ad10dbf3"
                        + "24ea534271ae11a5b10b92b52da258ffd92d62567916f0dbf2bf00bf51c617ca4ee05a";
            responseCipherText =
                    "6464646464646464646464646464646464646464646464646464646464646464b33e"
                            + "dd2665c9200931580a82cc6ceca195ed390bad54a199ba2e72bc16ef9a";
        }
        testVectors.add(
                new OhttpTestVector(
                        /* keyId= */ 2,
                        /* seed= */ "cccccccccccccccccccccccccccccccc",
                        expectedEnc,
                        /* plainText= */ "test request 4",
                        requestCipherText,
                        /* responsePlainText= */ "test response 4",
                        responseCipherText,
                        getKeyConfig(2)));

        return testVectors;
    }

    public static class OhttpTestVector {
        public int keyId;
        public String seed;
        public String expectedEnc;
        public String plainText;
        public String requestCipherText;
        public String responsePlainText;
        public String responseCipherText;
        public ObliviousHttpKeyConfig keyConfig;

        public OhttpTestVector(
                int keyId,
                String seed,
                String expectedEnc,
                String plainText,
                String requestCipherText,
                String responsePlainText,
                String responseCipherText,
                ObliviousHttpKeyConfig keyConfig) {
            this.keyId = keyId;
            this.seed = seed;
            this.expectedEnc = expectedEnc;
            this.plainText = plainText;
            this.requestCipherText = requestCipherText;
            this.responsePlainText = responsePlainText;
            this.responseCipherText = responseCipherText;
            this.keyConfig = keyConfig;
        }
    }

    private static ObliviousHttpKeyConfig getKeyConfig(int keyIdentifier)
            throws InvalidKeySpecException {
        byte[] keyId = new byte[1];
        keyId[0] = (byte) (keyIdentifier & 0xFF);
        String keyConfigHex =
                BaseEncoding.base16().lowerCase().encode(keyId)
                        + "0020"
                        + SERVER_PUBLIC_KEY
                        + "000400010002";
        return ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                BaseEncoding.base16().lowerCase().decode(keyConfigHex));
    }
}
