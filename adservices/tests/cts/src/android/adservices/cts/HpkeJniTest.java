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

package android.adservices.cts;

import com.android.adservices.HpkeJni;

import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;

public class HpkeJniTest {

    private static final byte[] ASSOCIATED_DATA = "associated_data".getBytes();
    private static final byte[] PLAIN_TEXT = "plain_text".getBytes();
    private static final byte[] PUBLIC_KEY =
            Base64.getDecoder().decode("rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=".getBytes());

    @Test
    public void testHpkeEncrypt_Success() {
        final byte[] result = HpkeJni.encrypt(PUBLIC_KEY, PLAIN_TEXT, ASSOCIATED_DATA);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }

    @Test
    public void testHpkeEncrypt_publicKeyNull_fail() {
        final byte[] result = HpkeJni.encrypt(/* publicKey= */ null, PLAIN_TEXT, ASSOCIATED_DATA);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_publicKeyShorterThan32_fail() {
        final byte[] shortPublicKey = new byte[31];
        final byte[] result = HpkeJni.encrypt(shortPublicKey, PLAIN_TEXT, ASSOCIATED_DATA);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_publicKeyLargerThan32_fail() {
        final byte[] shortPublicKey = new byte[33];
        final byte[] result = HpkeJni.encrypt(shortPublicKey, PLAIN_TEXT, ASSOCIATED_DATA);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_plainTextNull_fail() {
        final byte[] result = HpkeJni.encrypt(PUBLIC_KEY, /* plainText = */ null, ASSOCIATED_DATA);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_plainTextEmpty_success() {
        final byte[] emptyPlainText = new byte[] {};
        final byte[] result = HpkeJni.encrypt(PUBLIC_KEY, emptyPlainText, ASSOCIATED_DATA);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }

    @Test
    public void testHpkeEncrypt_associatedDataNull_fail() {
        final byte[] result = HpkeJni.encrypt(PUBLIC_KEY, PLAIN_TEXT, /* associatedData = */ null);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_associatedDataEmpty_success() {
        final byte[] emptyAssociatedData = new byte[] {};
        final byte[] result = HpkeJni.encrypt(PUBLIC_KEY, PLAIN_TEXT, emptyAssociatedData);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }
}
