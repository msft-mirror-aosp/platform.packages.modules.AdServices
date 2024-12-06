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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.FlagsFactory;
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
public class ObliviousHttpEncryptorImplTest extends AdServicesExtendedMockitoTestCase {

    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";

    @Mock AdSelectionEncryptionKeyManager mEncryptionKeyManagerMock;

    private EncryptionContextDao mEncryptionContextDao;

    private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private ExecutorService mLightweightExecutor;
    private DevContext mDevContext;

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionContextDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionServerDatabase.class)
                        .build()
                        .encryptionContextDao();

        mObliviousHttpEncryptor =
                new ObliviousHttpEncryptorImpl(
                        mEncryptionKeyManagerMock, mEncryptionContextDao, mLightweightExecutor);

        mDevContext = DevContext.builder(mPackageName).setDeviceDevOptionsEnabled(true).build();

        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void test_encryptBytes_invalidPlainText() {
        assertThrows(
                NullPointerException.class,
                () -> mObliviousHttpEncryptor.encryptBytes(null, 1L, 1000L, null, mDevContext));
    }

    @Test
    public void test_encryptBytes_success() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000L, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        mObliviousHttpEncryptor
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
                .startsWith("04002000010002");
    }

    @Test
    public void test_encryptBytes_success_withServerAuctionMediaTypeChange() throws Exception {
        doReturn(true).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000L, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        mObliviousHttpEncryptor
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
                .startsWith("0004002000010002");
    }

    @Test
    public void test_decryptBytes_invalidEncryptedBytes() {
        assertThrows(
                NullPointerException.class, () -> mObliviousHttpEncryptor.decryptBytes(null, 1L));
    }

    /** Test that an exception is thrown if encryption context is absent. */
    @Test
    public void test_decryptBytes_noEncryptionContext_throwsException() {
        String responseCipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                        + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        assertThrows(
                IllegalArgumentException.class,
                () -> mObliviousHttpEncryptor.decryptBytes(responseCipherTextBytes, 1L));
    }

    @Test
    public void test_decryptBytes_success() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes =
                mObliviousHttpEncryptor
                        .encryptBytes(plainTextBytes, 1L, 1000L, null, mDevContext)
                        .get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();
        assertThat(mEncryptionContextDao.getEncryptionContext(1L, AUCTION)).isNotNull();

        String responseCipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                        + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        mObliviousHttpEncryptor.decryptBytes(responseCipherTextBytes, 1L);
    }

    @Test
    public void test_decryptBytes_success_withServerAuctionMediaTypeChange() throws Exception {
        doReturn(true).when(mMockFlags).getFledgeAuctionServerMediaTypeChangeEnabled();
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        AUCTION, 1000, null, mDevContext))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes =
                mObliviousHttpEncryptor
                        .encryptBytes(plainTextBytes, 1L, 1000L, null, mDevContext)
                        .get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();
        assertThat(mEncryptionContextDao.getEncryptionContext(1L, AUCTION)).isNotNull();

        String responseCipherText =
                "37bea600af9cab94b009b0f4dac9576b6b118142e67eb64cffaf52ef72c82cfe06e2a"
                        + "d6ffb09334c60dd4de5b1339f2e13eab21a662fac2effab65ed959d58";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        mObliviousHttpEncryptor.decryptBytes(responseCipherTextBytes, 1L);
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
