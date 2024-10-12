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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.adservices.adselection.ObliviousHttpEncryptorWithSeedImpl;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.devapi.DevContext;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;

@SpyStatic(FlagsFactory.class)
public final class ObliviousHttpEncryptorWithSeedImplTest
        extends AdServicesExtendedMockitoTestCase {
    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";

    @Mock AdSelectionEncryptionKeyManager mEncryptionKeyManagerMock;
    private ExecutorService mLightweightExecutor;
    private EncryptionContextDao mEncryptionContextDao;
    private DevContext mDevContext;

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionContextDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionServerDatabase.class)
                        .build()
                        .encryptionContextDao();
        mDevContext = DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build();

        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void test_encryptBytes_success() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000L, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        String expectedCipherText =
                "040020000100021cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d"
                        + "0b18cb9a672ef2da3b97acee493624b9959f0fc6df008a6f0701c923c5a60ed0ed2c34";

        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        encryptor
                                                .encryptBytes(
                                                        plainTextBytes,
                                                        1L,
                                                        1000L,
                                                        null,
                                                        mDevContext)
                                                .get()))
                // Only the Ohttp header containing key ID and algorithm IDs is same across
                // multiple test runs since, a random seed is used to generate rest of the
                // cipher text.
                .isEqualTo(expectedCipherText);
    }

    @Test
    public void test_encryptBytes_success_withServerAuctionMediaTypeChange() throws Exception {
        doReturn(true).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000L, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        String expectedCipherText =
                "00040020000100021cf579aba45a10ba1d1ef06d91fca2aa9ed0a115051565315540"
                    + "5d0b18cb9a6770fbc40afc43d174f4b43cad7157d7b82b42f00aba7333d5f6c998918cca";
        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        encryptor
                                                .encryptBytes(
                                                        plainTextBytes,
                                                        1L,
                                                        1000L,
                                                        null,
                                                        mDevContext)
                                                .get()))
                // Only the Ohttp header containing key ID and algorithm IDs is same across
                // multiple test runs since, a random seed is used to generate rest of the
                // cipher text.
                .isEqualTo(expectedCipherText);
    }

    @Test
    public void test_decryptBytes_invalidEncryptedBytes() {
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);
        assertThrows(NullPointerException.class, () -> encryptor.decryptBytes(null, 1L));
    }

    @Test
    public void test_decryptBytes_success() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes =
                encryptor.encryptBytes(plainTextBytes, 1L, 1000L, null, mDevContext).get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();
        assertThat(mEncryptionContextDao.getEncryptionContext(1L, AUCTION)).isNotNull();

        String responseCipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                        + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        String expectedPlainText = "test response 1";
        assertThat(
                        new String(
                                encryptor.decryptBytes(responseCipherTextBytes, 1L),
                                StandardCharsets.UTF_8))
                .isEqualTo(expectedPlainText);
    }

    @Test
    public void test_decryptBytes_success_withServerAuctionMediaTypeChange() throws Exception {
        doReturn(true).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes =
                encryptor.encryptBytes(plainTextBytes, 1L, 1000L, null, mDevContext).get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();
        assertThat(mEncryptionContextDao.getEncryptionContext(1L, AUCTION)).isNotNull();

        String responseCipherText =
                "23abbbe3bc06290c060088a306244a470b60e2945c1fd7ea3a4e74a468331d0c85c6"
                        + "4533368103e556869020374eb84168a3cb27958b7bbfe7df861073a4ed";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        String expectedPlainText = "test response 1";
        assertThat(
                        new String(
                                encryptor.decryptBytes(responseCipherTextBytes, 1L),
                                StandardCharsets.UTF_8))
                .isEqualTo(expectedPlainText);
    }

    private ObliviousHttpKeyConfig getKeyConfig(int keyIdentifier) throws InvalidKeySpecException {
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
